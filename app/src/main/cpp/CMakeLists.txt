cmake_minimum_required(VERSION 3.22.1)
project(chessforge_sf CXX)

# Compilation de Stockfish en bibliotheque partagee, pilotee par jni_bridge.cpp.
#
# Android interdit d'executer un binaire depuis le repertoire de donnees d'une appli :
# Stockfish tourne donc *dans* le processus, sa boucle UCI lisant deux tubes rediriges
# vers stdin/stdout. Les sources ne sont presentes que si tools/fetch_stockfish.ps1 a
# ete lance ; sinon ce fichier n'est meme pas evalue (voir app/build.gradle.kts).
#
# Les drapeaux reproduisent ceux de `make ARCH=armv8-dotprod COMP=ndk` du Makefile
# officiel de Stockfish 19.

set(SF_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/stockfish/src)

if (NOT EXISTS ${SF_DIR}/uci.cpp)
    message(FATAL_ERROR "Sources Stockfish absentes de ${SF_DIR}. Lancez tools/fetch_stockfish.ps1.")
endif()

# src/universal/ est volontairement exclu : ces fichiers ne servent qu'aux binaires
# "universels" multi-architectures et utilisent des extensions que le NDK ne compile pas.
file(GLOB SF_SOURCES
        ${SF_DIR}/*.cpp
        ${SF_DIR}/nnue/*.cpp
        ${SF_DIR}/nnue/features/*.cpp
        ${SF_DIR}/nnue/layers/*.cpp
        ${SF_DIR}/syzygy/*.cpp
)

# main.cpp est conserve mais son point d'entree est renomme : le pont JNI l'appelle
# dans un thread au lieu de laisser le systeme demarrer un processus.
set_source_files_properties(${SF_DIR}/main.cpp PROPERTIES COMPILE_DEFINITIONS "main=stockfish_main")

add_library(chessforge_sf SHARED ${SF_SOURCES} jni_bridge.cpp)

target_include_directories(chessforge_sf PRIVATE ${SF_DIR})

target_compile_definitions(chessforge_sf PRIVATE
        NDEBUG
        # Le reseau NNUE est livre en asset et charge a l'execution, plutot qu'incorpore
        # au binaire : la bibliotheque reste legere et le reseau est remplacable.
        NNUE_EMBEDDING_OFF
)

target_compile_options(chessforge_sf PRIVATE
        -O3
        # Stockfish se compile officiellement sans exceptions.
        -fno-exceptions
        -fvisibility=hidden
        -Wno-unused-variable
        -Wno-unused-but-set-variable
        -Wno-deprecated-declarations
)

if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(chessforge_sf PRIVATE
            IS_64BIT
            USE_POPCNT
            USE_NEON=8
            USE_NEON_DOTPROD
    )
    # ARMv8.2 + dot product : present sur tous les Snapdragon/Exynos recents.
    target_compile_options(chessforge_sf PRIVATE -march=armv8.2-a+dotprod)
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    target_compile_definitions(chessforge_sf PRIVATE USE_NEON=7)
    target_compile_options(chessforge_sf PRIVATE -mfpu=neon)
endif()

find_library(LOG_LIB log)
target_link_libraries(chessforge_sf ${LOG_LIB} atomic)

# Pages memoire de 16 Ko : exigees par Google Play pour tout code natif depuis le
# 1er novembre 2025, et indispensables sur les appareils 64 bits recents qui
# n'utilisent plus des pages de 4 Ko. Le NDK r27 ne l'applique pas par defaut.
target_link_options(chessforge_sf PRIVATE "-Wl,-z,max-page-size=16384")
set_target_properties(chessforge_sf PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
