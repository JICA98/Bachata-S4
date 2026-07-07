#!/system/bin/sh
root="$1"
loader="$2"
export VK_ICD_FILENAMES="$root/host/vulkan/icd.d/freedreno_icd.json"
if [ "$3" = x64 ]; then
    export BOX64_LOG=1
    export BOX64_LOAD_ADDR=0x6000000000
    export PROBE_RESERVE_PS4=1
    export BOX64_PREFER_WRAPPED=1
    export BOX64_LD_LIBRARY_PATH="$root/lib/x86_64-linux-gnu:$root/lib64"
    exec "$loader" --library-path "$root/host" "$4" /data/user/0/com.bachatas4.android/files/vulkan-map-probe-x64
fi
exec "$loader" --library-path "$root/host" /data/user/0/com.bachatas4.android/files/vulkan-map-probe
