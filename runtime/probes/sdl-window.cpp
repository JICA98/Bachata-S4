#include <SDL.h>

#include <chrono>
#include <cstdio>
#include <thread>

int main() {
    if (SDL_Init(SDL_INIT_VIDEO) != 0) return 1;
    SDL_Window* window = SDL_CreateWindow(
        "Bachata S4 SDL probe",
        SDL_WINDOWPOS_CENTERED,
        SDL_WINDOWPOS_CENTERED,
        1280,
        720,
        SDL_WINDOW_VULKAN | SDL_WINDOW_RESIZABLE);
    if (window == nullptr) {
        SDL_Quit();
        return 2;
    }

    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(3);
    bool quit_requested = false;
    while (!quit_requested && std::chrono::steady_clock::now() < deadline) {
        SDL_Event event;
        while (SDL_PollEvent(&event)) {
            if (event.type == SDL_QUIT) quit_requested = true;
            if (event.type == SDL_WINDOWEVENT && event.window.event == SDL_WINDOWEVENT_SIZE_CHANGED) {
                SDL_LogDebug(SDL_LOG_CATEGORY_VIDEO, "resized to %dx%d", event.window.data1, event.window.data2);
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(16));
    }

    SDL_DestroyWindow(window);
    SDL_Quit();
    if (quit_requested) return 3;
    std::puts("BACHATA_SDL_OK");
    return 0;
}
