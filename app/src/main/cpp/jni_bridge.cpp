// Pont JNI entre ChessForge (Kotlin) et la boucle UCI de Stockfish.
//
// Principe : on cree deux tubes, on redirige stdin/stdout du processus vers eux, puis
// on lance la fonction principale de Stockfish dans un thread. Cote Kotlin, ecrire une
// commande revient a ecrire dans le premier tube, lire une reponse a lire le second.
//
// C'est la seule approche viable sur Android recent, qui interdit d'executer un
// binaire depuis le repertoire de donnees d'une application.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <unistd.h>

#define LOG_TAG "ChessForgeSF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Point d'entree de Stockfish, renomme a la compilation (voir CMakeLists.txt).
//
// Pas de `extern "C"` ici : `main` est une fonction speciale non decoree, mais une fois
// renommee par la macro elle redevient une fonction C++ ordinaire, donc decoree. La
// declaration doit etre en C++ pour que le symbole corresponde a la definition.
int stockfish_main(int argc, char *argv[]);

namespace {

std::atomic<bool> g_started{false};
int g_toEngine[2] = {-1, -1};   // Kotlin -> moteur (stdin du moteur)
int g_fromEngine[2] = {-1, -1}; // moteur -> Kotlin (stdout du moteur)
FILE *g_output = nullptr;
std::thread g_engineThread;

void engineLoop() {
    char program[] = "stockfish";
    char *argv[] = {program, nullptr};
    stockfish_main(1, argv);
    LOGI("La boucle UCI de Stockfish s'est terminee.");
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_chessforge_engine_sf_NativeBridge_nativeStart(JNIEnv *, jclass) {
    if (g_started.load()) return JNI_TRUE;

    if (pipe(g_toEngine) != 0 || pipe(g_fromEngine) != 0) {
        LOGE("Creation des tubes impossible : %s", strerror(errno));
        return JNI_FALSE;
    }

    // Rediriger les entrees/sorties standard du processus vers les tubes.
    if (dup2(g_toEngine[0], STDIN_FILENO) < 0 || dup2(g_fromEngine[1], STDOUT_FILENO) < 0) {
        LOGE("Redirection des flux impossible : %s", strerror(errno));
        return JNI_FALSE;
    }

    // Sortie ligne par ligne : sans cela, la sortie vers un tube serait mise en tampon
    // par blocs et les reponses du moteur arriveraient en retard.
    setvbuf(stdout, nullptr, _IOLBF, 4096);
    setvbuf(stdin, nullptr, _IOLBF, 4096);

    g_output = fdopen(g_fromEngine[0], "r");
    if (g_output == nullptr) {
        LOGE("Ouverture du tube de lecture impossible : %s", strerror(errno));
        return JNI_FALSE;
    }

    g_started.store(true);
    g_engineThread = std::thread(engineLoop);
    g_engineThread.detach();
    LOGI("Stockfish demarre dans le processus.");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_chessforge_engine_sf_NativeBridge_nativeWrite(JNIEnv *env, jclass, jstring command) {
    if (!g_started.load()) return;
    const char *chars = env->GetStringUTFChars(command, nullptr);
    if (chars == nullptr) return;
    std::string line(chars);
    env->ReleaseStringUTFChars(command, chars);
    line.push_back('\n');
    ssize_t written = write(g_toEngine[1], line.c_str(), line.size());
    if (written < 0) LOGE("Ecriture vers le moteur impossible : %s", strerror(errno));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_chessforge_engine_sf_NativeBridge_nativeReadLine(JNIEnv *env, jclass) {
    if (!g_started.load() || g_output == nullptr) return nullptr;
    char buffer[8192];
    if (fgets(buffer, sizeof(buffer), g_output) == nullptr) return nullptr;
    size_t length = strlen(buffer);
    while (length > 0 && (buffer[length - 1] == '\n' || buffer[length - 1] == '\r')) {
        buffer[--length] = '\0';
    }
    return env->NewStringUTF(buffer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_chessforge_engine_sf_NativeBridge_nativeStop(JNIEnv *, jclass) {
    if (!g_started.load()) return;
    // Uniquement "stop" : envoyer "quit" terminerait la boucle UCI pour de bon, et
    // toute analyse ulterieure se bloquerait en attendant une reponse qui ne viendrait
    // jamais. Le moteur vit aussi longtemps que le processus de l'application.
    const char stop[] = "stop\n";
    ssize_t ignored = write(g_toEngine[1], stop, sizeof(stop) - 1);
    (void) ignored;
}
