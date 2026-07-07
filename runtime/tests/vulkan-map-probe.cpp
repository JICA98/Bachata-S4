#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>

#include <cstdio>
#include <cstdlib>
#include <dlfcn.h>
#include <sys/mman.h>

template <typename T>
T Load(void* library, const char* name) {
    return reinterpret_cast<T>(dlsym(library, name));
}

int main() {
    if (std::getenv("PROBE_RESERVE_PS4")) {
        constexpr uintptr_t base = 0x400000;
        constexpr size_t size = 0x6000000000 - base;
        void* reserved = mmap(reinterpret_cast<void*>(base), size, PROT_NONE,
                              MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED | MAP_NORESERVE, -1, 0);
        std::printf("reserve=%p size=%zx\n", reserved, size);
    }
    void* library = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!library) {
        std::fprintf(stderr, "dlopen: %s\n", dlerror());
        return 1;
    }
    auto get_instance_proc = Load<PFN_vkGetInstanceProcAddr>(library, "vkGetInstanceProcAddr");
    auto create_instance = reinterpret_cast<PFN_vkCreateInstance>(
        get_instance_proc(VK_NULL_HANDLE, "vkCreateInstance"));
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo instance_info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    instance_info.pApplicationInfo = &app;
    VkInstance instance{};
    VkResult result = create_instance(&instance_info, nullptr, &instance);
    std::printf("create_instance=%d\n", result);
    if (result != VK_SUCCESS) return 2;

    auto enumerate = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
        get_instance_proc(instance, "vkEnumeratePhysicalDevices"));
    uint32_t count = 1;
    VkPhysicalDevice physical{};
    result = enumerate(instance, &count, &physical);
    std::printf("enumerate=%d count=%u\n", result, count);
    if (result != VK_SUCCESS || count == 0) return 3;

    auto get_queues = reinterpret_cast<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(
        get_instance_proc(instance, "vkGetPhysicalDeviceQueueFamilyProperties"));
    uint32_t queue_count = 0;
    get_queues(physical, &queue_count, nullptr);
    VkQueueFamilyProperties queues[32]{};
    if (queue_count > 32) queue_count = 32;
    get_queues(physical, &queue_count, queues);
    uint32_t queue_family = 0;
    while (queue_family < queue_count && queues[queue_family].queueCount == 0) ++queue_family;
    if (queue_family == queue_count) return 4;

    float priority = 1.0f;
    VkDeviceQueueCreateInfo queue_info{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    queue_info.queueFamilyIndex = queue_family;
    queue_info.queueCount = 1;
    queue_info.pQueuePriorities = &priority;
    VkDeviceCreateInfo device_info{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    device_info.queueCreateInfoCount = 1;
    device_info.pQueueCreateInfos = &queue_info;
    auto create_device = reinterpret_cast<PFN_vkCreateDevice>(
        get_instance_proc(instance, "vkCreateDevice"));
    VkDevice device{};
    result = create_device(physical, &device_info, nullptr, &device);
    std::printf("create_device=%d\n", result);
    if (result != VK_SUCCESS) return 5;

    auto get_memory = reinterpret_cast<PFN_vkGetPhysicalDeviceMemoryProperties>(
        get_instance_proc(instance, "vkGetPhysicalDeviceMemoryProperties"));
    VkPhysicalDeviceMemoryProperties properties{};
    get_memory(physical, &properties);
    uint32_t type = 0;
    while (type < properties.memoryTypeCount &&
           !(properties.memoryTypes[type].propertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)) ++type;
    if (type == properties.memoryTypeCount) return 6;

    auto get_device_proc = reinterpret_cast<PFN_vkGetDeviceProcAddr>(
        get_instance_proc(instance, "vkGetDeviceProcAddr"));
    auto allocate = reinterpret_cast<PFN_vkAllocateMemory>(get_device_proc(device, "vkAllocateMemory"));
    auto map = reinterpret_cast<PFN_vkMapMemory>(get_device_proc(device, "vkMapMemory"));
    VkMemoryAllocateInfo allocate_info{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocate_info.allocationSize = 4096;
    allocate_info.memoryTypeIndex = type;
    VkDeviceMemory memory{};
    result = allocate(device, &allocate_info, nullptr, &memory);
    std::printf("allocate=%d memory=%p type=%u\n", result, reinterpret_cast<void*>(memory), type);
    if (result != VK_SUCCESS) return 7;
    void* data = nullptr;
    result = map(device, memory, 0, VK_WHOLE_SIZE, 0, &data);
    std::printf("map=%d data=%p\n", result, data);
    return result == VK_SUCCESS ? 0 : 8;
}
