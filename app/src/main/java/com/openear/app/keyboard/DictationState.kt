package com.openear.app.keyboard

enum class DictationPhase { Idle, Listening, Transcribing, Inserted, Error }

enum class DictationError(val message: String) {
    PermissionDenied("Microphone permission needed. Allow it, then tap the mic again."),
    MicUnavailable("Microphone is busy or unavailable. Close other apps using it and retry."),
    Interrupted("Recording was interrupted. Tap the mic to try again."),
    NoSpeech("No speech detected. Tap the mic and speak closer to the phone."),
    ModelMissing("No speech model installed. Open OpenEar settings to download one."),
    ModelProblem("The speech model could not be loaded. Re-download it in OpenEar."),
    LowMemory("Not enough memory for this model. Try the Tiny model in OpenEar."),
    TranscriptionFailed("Transcription failed. Tap the mic to retry."),
    FieldGone("The text field changed, so nothing was inserted. Tap the field and retry."),
    Restricted("OpenEar does not dictate into password or secure fields."),
    Busy("Still transcribing. Please wait a moment."),
}

data class DictationState(
    val phase: DictationPhase = DictationPhase.Idle,
    val error: DictationError? = null,
) {
    val label: String
        get() = when (phase) {
            DictationPhase.Idle -> "Tap to speak"
            DictationPhase.Listening -> "Listening… tap to finish"
            DictationPhase.Transcribing -> "Transcribing…"
            DictationPhase.Inserted -> "Text inserted"
            DictationPhase.Error -> error?.message ?: "Something went wrong"
        }
}
