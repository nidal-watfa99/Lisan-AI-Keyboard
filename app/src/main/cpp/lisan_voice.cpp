#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include "whisper.h"

struct Cancel {
    JavaVM * vm;
    jobject flag;
    jmethodID get;
};
static bool aborted(void * p) {
    auto &c = *static_cast<Cancel *>(p);
    JNIEnv * e = nullptr;
    auto state = c.vm->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_1_6);
    if (state != JNI_OK) {
#ifdef __ANDROID__
        if (c.vm->AttachCurrentThread(&e,nullptr) != JNI_OK) return true;
#else
        if (c.vm->AttachCurrentThread(reinterpret_cast<void **>(&e),nullptr) != JNI_OK) return true;
#endif
    }
    bool result = e->CallBooleanMethod(c.flag,c.get);
    if (state != JNI_OK) c.vm->DetachCurrentThread();
    return result;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_lisan_keyboard_voice_WhisperNative_decode(JNIEnv * e, jobject, jstring model, jshortArray pcm,
                                                   jstring language, jobject flag, jint threads) {
    auto length=e->GetArrayLength(pcm);
    if (length < 3200 || length > 480000) return nullptr;
    Cancel cancel{};
    e->GetJavaVM(&cancel.vm);
    cancel.flag=e->NewGlobalRef(flag);
    if (!cancel.flag) return nullptr;
    cancel.get=e->GetMethodID(e->GetObjectClass(flag),"get","()Z");
    if (!cancel.get) { e->DeleteGlobalRef(cancel.flag); return nullptr; }
    auto file = e->GetStringUTFChars(model,nullptr);
    std::string path = file ? file : "";
    if (file) e->ReleaseStringUTFChars(model,file);
    auto lang = e->GetStringUTFChars(language,nullptr);
    std::string tag = lang ? lang : "";
    if (lang) e->ReleaseStringUTFChars(language, lang);
    auto options=whisper_context_default_params();
    options.use_gpu=false;
    auto * ctx=aborted(&cancel)? nullptr : whisper_init_from_file_with_params(path.c_str(),options);
    if (!ctx) { e->DeleteGlobalRef(cancel.flag); return nullptr; }
    std::vector<jshort> raw(length);
    e->GetShortArrayRegion(pcm,0,length,raw.data());
    std::vector<float> audio(length);
    std::transform(raw.begin(),raw.end(),audio.begin(),[](jshort v){ return v / 32768.f; });
    std::fill(raw.begin(),raw.end(),0);
    auto params=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads=std::clamp(int(threads),1,4);
    params.translate=false;
    params.no_context=true;
    params.print_progress=false;
    params.print_realtime=false;
    params.print_timestamps=false;
    params.print_special=false;
    params.suppress_blank=true;
    params.temperature_inc=0;
    params.language = tag.c_str();
    params.abort_callback=aborted;
    params.abort_callback_user_data=&cancel;
    std::string result;
    const bool success = !aborted(&cancel) && whisper_full(ctx,params,audio.data(),length)==0 && !aborted(&cancel);
    if (success) {
        const int count=whisper_full_n_segments(ctx);
        for (int i=0;i<count;i++) result+=whisper_full_get_segment_text(ctx,i);
    }
    whisper_free(ctx);
    std::fill(audio.begin(),audio.end(),0);
    e->DeleteGlobalRef(cancel.flag);
    if (!success || result.size()>32000) return nullptr;
    auto answer=e->NewByteArray(static_cast<jsize>(result.size()));
    if(answer && !result.empty())e->SetByteArrayRegion(answer,0,static_cast<jsize>(result.size()),
                                                      reinterpret_cast<const jbyte *>(result.data()));
    return answer;
}
