import re

with open(r"c:\NexaIA\app\src\main\java\com\nexa\ai\viewmodel\NexaViewModel.kt", "r", encoding="utf-8") as f:
    code = f.read()

# Replace speechManager.startVoiceAudioSession() and stop
code = code.replace("speechManager.startVoiceAudioSession()", "")
code = code.replace("speechManager.stopVoiceAudioSession()", "")

# Replace speechManager.startBargeInMonitor()
code = code.replace("speechManager.startBargeInMonitor()", "")
code = code.replace("speechManager.stopBargeInMonitor()", "")

# Remove the whole onBargeInDetected block
code = re.sub(r"// Barge-in: AudioRecord detected user voice while AI was speaking\s*speechManager\.onBargeInDetected = \{.*?(?=\s*speechManager\.onSpeechResult = \{)", "", code, flags=re.DOTALL)

with open(r"c:\NexaIA\app\src\main\java\com\nexa\ai\viewmodel\NexaViewModel.kt", "w", encoding="utf-8") as f:
    f.write(code)

print("NexaViewModel.kt cleaned!")
