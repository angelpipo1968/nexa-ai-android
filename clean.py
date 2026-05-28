import re

def remove_function(code, func_name):
    # Regex to find a function definition and its entire body
    # This assumes standard indentation and matching braces
    pattern = r"(?:private |public )?fun " + func_name + r"\s*\([^)]*\)(?:\s*:\s*[A-Za-z<>]+)?\s*\{"
    match = re.search(pattern, code)
    if not match:
        return code
    
    start_idx = match.start()
    brace_count = 0
    in_function = False
    
    for i in range(match.end() - 1, len(code)):
        if code[i] == '{':
            brace_count += 1
            in_function = True
        elif code[i] == '}':
            brace_count -= 1
            
        if in_function and brace_count == 0:
            end_idx = i + 1
            # Return code with the function removed
            return code[:start_idx] + code[end_idx:]
            
    return code

with open(r"c:\NexaIA\app\src\main\java\com\nexa\ai\viewmodel\SpeechManager.kt", "r", encoding="utf-8") as f:
    code = f.read()

funcs_to_remove = [
    "startBargeInMonitor", "stopBargeInMonitor", 
    "registerScoStateReceiver", "unregisterScoStateReceiver",
    "detectBluetoothSco", "startBluetoothSco", "startBluetoothScoLegacyFallback", "stopBluetoothSco",
    "initProximitySensor", "enableProximitySensor", "disableProximitySensor", "updateAudioRoutingForProximity",
    "setSpeakerphoneOn", "startVoiceAudioSession", "stopVoiceAudioSession", "reapplyHandsFreeRouting",
    "isSpeakerphoneActive", "boostVolumeForHandsFree"
]

for func in funcs_to_remove:
    code = remove_function(code, func)

# Also replace some specific lines that call these functions in initialize() and initTTS()
code = re.sub(r"\s*detectBluetoothSco\(\)", "", code)
code = re.sub(r"\s*registerScoStateReceiver\(\)", "", code)
code = re.sub(r"\s*initProximitySensor\(\)", "", code)

with open(r"c:\NexaIA\app\src\main\java\com\nexa\ai\viewmodel\SpeechManager.kt", "w", encoding="utf-8") as f:
    f.write(code)

print("SpeechManager.kt cleaned!")
