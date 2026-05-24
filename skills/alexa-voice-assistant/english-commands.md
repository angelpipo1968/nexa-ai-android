# English Commands Reference — Bilingual Support

This file maps all English voice commands to their Spanish equivalents and intended actions.
The skill auto-detects the user's language and responds in the same language throughout the session.

---

## 🌐 Language Detection Rules

- If user speaks English → respond entirely in English
- If user speaks Spanish → respond entirely in Spanish
- If mixed → match the dominant language of the request
- Language can switch mid-session: follow the user
- Never mix languages in the same response

**English wake words:** "Alexa", "Hey Nexa", "Nexa"

---

## 🕐 Time & Date

| English Command | Action |
|----------------|--------|
| "What time is it?" | Report current time |
| "What day is it today?" | Report current date |
| "What's today's date?" | Report full date |
| "How many days until Christmas?" | Countdown to date |
| "What time is it in Tokyo?" | Time zone lookup |
| "What day of the week is [date]?" | Day calculation |

**English response format:**
> "It's 3:45 in the afternoon." / "Today is Tuesday, May 24th."

---

## ⏰ Alarms & Timers

| English Command | Action |
|----------------|--------|
| "Set an alarm for 7 AM" | Alarm: 07:00 |
| "Wake me up at 6:30" | Alarm: 06:30 |
| "Set a timer for 10 minutes" | Timer: 10:00 |
| "Set a 1-hour timer" | Timer: 60:00 |
| "Cancel my alarm" | Delete alarm |
| "Cancel all alarms" | Delete all alarms |
| "Snooze" | Snooze: +9 min |
| "How much time is left?" | Timer status |
| "Pause the timer" | Timer pause |
| "Resume the timer" | Timer resume |
| "Set a repeating alarm every weekday at 7" | Recurring alarm |
| "Set an alarm for weekends at 9 AM" | Weekend alarm |
| "What alarms do I have?" | List all alarms |
| "Set a pasta timer for 8 minutes" | Named timer |

**Confirmation responses:**
> "⏰ Alarm set for 7:00 AM." / "⏱️ 10-minute timer started."

---

## 🌤️ Weather

| English Command | Action |
|----------------|--------|
| "What's the weather like?" | Current weather |
| "What's the weather in London?" | City-specific |
| "Will it rain today?" | Rain probability |
| "Do I need an umbrella?" | Practical recommendation |
| "What's the temperature outside?" | Temperature only |
| "What's the forecast for this week?" | 7-day forecast |
| "Is it going to snow tomorrow?" | Snow check |
| "How's the weather this weekend?" | Weekend forecast |
| "Is there a storm coming?" | Weather alert |
| "What should I wear today?" | Clothing recommendation |

**English response format:**
```
📍 London — Tuesday
🌡️ 18°C (Feels like 16°C)
💧 Humidity: 72%  💨 Wind: 15 km/h
⛅ Partly cloudy
🌧️ Rain: 30% chance

Tomorrow: ☀️ 22°/14°C
```

---

## 📋 Lists

| English Command | Action |
|----------------|--------|
| "Add milk to my shopping list" | Add item |
| "Add eggs, bread, and butter" | Add multiple items |
| "What's on my shopping list?" | Read list |
| "Remove bread from my list" | Delete item |
| "Check off milk" | Mark as bought |
| "Clear my shopping list" | Delete all |
| "What's on my to-do list?" | Task list |
| "Add 'call dentist' to my to-do list" | Add task |
| "Mark 'call dentist' as done" | Complete task |
| "Show my grocery list organized by section" | Organized list |

---

## 🎵 Music

| English Command | Action |
|----------------|--------|
| "Play some music" | Start playback |
| "Play jazz" | Genre |
| "Play [artist name]" | By artist |
| "Play [song title]" | By song |
| "Pause" / "Resume" | Playback control |
| "Next song" / "Skip" | Next track |
| "Previous song" | Previous track |
| "Volume up" / "Volume down" | Adjust volume |
| "Set volume to 50%" | Exact volume |
| "Mute" / "Unmute" | Toggle mute |
| "Shuffle on" / "Shuffle off" | Shuffle mode |
| "Repeat this song" | Loop single |
| "Play music for working out" | Workout playlist |
| "Play relaxing music" | Chill playlist |
| "Play music to help me sleep" | Sleep music |
| "Play music for studying" | Focus music |
| "Play rain sounds" | Ambient sound |
| "Play white noise" | White noise |
| "Stop music in 30 minutes" | Sleep timer |
| "What song is this?" | Track info |
| "Who sings this?" | Artist info |

---

## 💡 Smart Home

| English Command | Action |
|----------------|--------|
| "Turn on the lights" | Lights on |
| "Turn off the lights" | Lights off |
| "Dim the lights to 30%" | Brightness: 30% |
| "Set the lights to blue" | Color: blue |
| "Good night mode" | Lights: 10%, warm |
| "Movie mode" | Lights: 20%, orange |
| "Set the thermostat to 70°F" | Setpoint: 21°C |
| "Turn on the AC" | AC on |
| "Turn on the heat" | Heat on |
| "What's the temperature inside?" | Current temp |
| "Turn on the TV" | TV on |
| "Turn off the TV" | TV off |
| "Volume up on TV" | TV volume + |
| "Lock the door" | Lock: true |
| "Unlock the door" | Lock: false |
| "Is the door locked?" | Lock status |
| "Turn on the dishwasher" | Appliance on |
| "Start the robot vacuum" | Roomba: start |
| "Turn on the coffee maker" | Coffee: on |
| "Open the garage" | Garage: open |
| "Close the garage" | Garage: close |
| "Goodnight" | Goodnight scene |
| "I'm leaving" | Away mode scene |
| "I'm home" | Welcome home scene |
| "Good morning" | Morning scene |
| "Movie time" | Cinema scene |
| "Party mode" | Party scene |

---

## 📞 Communication

| English Command | Action |
|----------------|--------|
| "Call Mom" | Call contact |
| "Call 555-1234" | Direct dial |
| "Video call John" | Video call |
| "Hang up" | End call |
| "Send a WhatsApp to Anna saying I'm on my way" | WhatsApp message |
| "Send a text to Mike: See you at 8" | SMS |
| "Read my messages" | Read unread messages |
| "Reply yes" | Quick reply |
| "Do I have any emails?" | Email check |
| "Read my emails" | Read inbox |
| "Send an email to boss@company.com" | Compose email |
| "Do not disturb" | DND: on |
| "Do not disturb until 9 AM" | DND with schedule |
| "Read my notifications" | Notification summary |
| "What did I miss?" | Summary of missed activity |
| "Announce dinner is ready" | Home broadcast |

---

## 📅 Calendar & Reminders

| English Command | Action |
|----------------|--------|
| "What do I have today?" | Today's events |
| "What's on my calendar tomorrow?" | Tomorrow's events |
| "What do I have this week?" | Weekly overview |
| "Am I free on Thursday?" | Availability check |
| "Add a meeting on Friday at 3 PM" | Create event |
| "Schedule a dentist appointment Monday at noon" | Create event |
| "Remind me to take my pills at 8 AM" | Create reminder |
| "Remind me to call Dad tomorrow" | Date reminder |
| "Remind me every Friday to send the report" | Recurring reminder |
| "Cancel my Tuesday meeting" | Delete event |
| "Move the meeting to 4 PM" | Reschedule |
| "What reminders do I have?" | List reminders |
| "Cancel the 7 AM reminder" | Delete reminder |

---

## 📰 News & Sports

| English Command | Action |
|----------------|--------|
| "What's the news?" | Top 5 headlines |
| "Give me the news" | Full news briefing |
| "Technology news" | Category: tech |
| "Sports news" | Category: sports |
| "Business news" | Category: economy |
| "World news" | International news |
| "More details on that story" | Expand last headline |
| "What happened with [topic]?" | Topic search |
| "How did [team] do?" | Sports result |
| "What's the score?" | Live score |
| "When does [team] play?" | Next game |
| "How's [team] doing in the standings?" | League table |
| "Formula 1 results" | F1 standings |

---

## 💰 Finance & Conversions

| English Command | Action |
|----------------|--------|
| "What's the exchange rate for euros?" | EUR/USD |
| "How much is Bitcoin?" | BTC price |
| "Convert 100 dollars to euros" | Currency conversion |
| "How's Apple stock doing?" | AAPL price |
| "How's the stock market today?" | Market overview |
| "What's 15% of 280?" | Math calculation |
| "What's the square root of 144?" | Math |
| "How many miles are in 10 kilometers?" | Unit conversion |
| "Convert 100 pounds to kilograms" | Weight conversion |
| "How many ounces in a cup?" | Cooking conversion |
| "What's 350°F in Celsius?" | Temperature conversion |

---

## 😄 Entertainment & Games

| English Command | Action |
|----------------|--------|
| "Tell me a joke" | Random joke |
| "Tell me a dad joke" | Dad joke |
| "Tell me a joke for kids" | Kid-friendly |
| "Another joke" | Next joke |
| "Let's play trivia" | 10-question trivia game |
| "Trivia about science" | Category trivia |
| "Hard question" | Difficulty: hard |
| "Let's play 20 questions" | 20Q game |
| "Let's play hangman" | Hangman game |
| "Tell me a story" | Short story |
| "Tell me a bedtime story" | Calm story |
| "Tell me a scary story" | Horror story |
| "Interactive story" | Choose your adventure |
| "Roll a dice" | 🎲 Random 1-6 |
| "Flip a coin" | 🪙 Heads or tails |
| "Give me a random number between 1 and 100" | Random number |
| "Tell me a fun fact" | Random fact |
| "Science fun fact" | Science fact |
| "Space fact" | Astronomy fact |
| "Riddle me this" | Riddle |
| "Give me a hint" | Hint for current riddle |
| "I give up" | Reveal answer |
| "Talk like a pirate" | 🏴‍☠️ Pirate mode |
| "Be my personal chef" | 👨‍🍳 Chef mode |
| "Back to normal" | Exit roleplay |

---

## 🍳 Recipes & Cooking

| English Command | Action |
|----------------|--------|
| "What can I make with chicken and rice?" | Ingredient-based recipe |
| "Give me a recipe for pasta carbonara" | Specific recipe |
| "Something quick for dinner" | Under 30 min recipes |
| "Vegetarian recipe for 4 people" | Filtered recipe |
| "Gluten-free dessert" | Dietary filter |
| "Next step" | Cooking assistance |
| "Repeat that step" | Repeat instruction |
| "How long does this step take?" | Timing info |
| "What temperature should the oven be?" | Temperature query |
| "How many cups is 250 grams of flour?" | Cooking conversion |
| "What does sauté mean?" | Culinary glossary |
| "Plan my meals for the week" | Weekly meal plan |
| "Generate a shopping list for the meal plan" | Auto shopping list |

---

## 🏃 Health & Fitness

| English Command | Action |
|----------------|--------|
| "Give me a 20-minute workout" | Workout routine |
| "Exercises for my back" | Targeted exercise |
| "Home leg workout" | No equipment |
| "5-minute warm-up" | Warm-up routine |
| "Yoga for beginners" | Yoga sequence |
| "Tabata timer" | 20s work / 10s rest |
| "Log 30 minutes of running" | Activity log |
| "Remind me to take my medication at 8 AM" | Medication reminder |
| "Did I take my medication?" | Check log |
| "Mark medication as taken" | Log dose |
| "How much water should I drink?" | Hydration advice |
| "How many calories in an apple?" | Nutrition info |
| "Guide me through breathing" | Breathing exercise |
| "I need to relax" | 4-7-8 breathing |
| "5-minute meditation" | Guided meditation |
| "What time should I go to sleep to wake up at 7?" | Sleep cycle calculator |
| "Log 7 hours of sleep" | Sleep log |
| "Tell me a sleep story" | Bedtime story |

---

## ✈️ Travel & Navigation

| English Command | Action |
|----------------|--------|
| "How do I get to downtown?" | Navigation |
| "Take me to [address]" | Turn-by-turn |
| "How long to get to work?" | ETA |
| "How's the traffic?" | Traffic report |
| "Is there a faster route?" | Alternative route |
| "Find parking near me" | Parking search |
| "Save where I parked" | Save parking location |
| "Where did I park?" | Retrieve parking |
| "Track my flight AA123" | Flight tracking |
| "What time does my flight leave?" | Departure info |
| "Is my flight delayed?" | Delay status |
| "Find me a hotel in Paris this weekend" | Hotel search |
| "Cheap flights to Rome in June" | Flight search |
| "Do I need a visa for Japan?" | Visa requirements |
| "What's the currency in Thailand?" | Currency info |
| "What time is it in New York?" | Time zone |
| "How do you say 'thank you' in Japanese?" | Translation |
| "What's a good restaurant near me?" | Local dining |

---

## 🛒 Shopping & Routines

| English Command | Action |
|----------------|--------|
| "Order paper towels" | Online order |
| "Where's my package?" | Delivery tracking |
| "When does my order arrive?" | Estimated delivery |
| "Reorder what I bought last week" | Repeat order |
| "How much have I spent this month?" | Expense summary |
| "I spent $45 at the grocery store" | Log expense |
| "My monthly budget is $1500" | Set budget |
| "How much is left in my budget?" | Budget check |
| "Start my morning routine" | Morning routine |
| "Start my bedtime routine" | Night routine |
| "I'm leaving" | Away routine |
| "I'm home" | Welcome home routine |
| "Create a routine called [name]" | Custom routine |
| "Run my [name] routine" | Execute routine |
| "What routines do I have?" | List routines |

---

## 👶 Kids & Education

| English Command | Action |
|----------------|--------|
| "Kids mode" | Enable parental filters |
| "Tell me a bedtime story" | Calm story for kids |
| "Tell me an animal story" | Animal protagonist |
| "Tell me a superhero story" | Adventure story |
| "Help me with multiplication tables" | Math practice |
| "What's 7 times 8?" | Quick math |
| "Practice the 6 times table" | Focused drill |
| "Teach me Spanish" | Language lesson |
| "How do you say 'dog' in French?" | Translation |
| "Why is the sky blue?" | Kid-friendly science |
| "How do rainbows form?" | Simple explanation |
| "Let's play animal trivia" | Kids quiz |
| "Tell me a riddle" | Kids riddle |
| "Sing the alphabet song" | ABC song |
| "Fun fact about dinosaurs" | Dino facts |
| "Help me with my homework" | Homework assistance |
| "What does [word] mean?" | Simple definition |

---

## 🌐 General Knowledge (English)

| English Command | Action |
|----------------|--------|
| "What is [topic]?" | General knowledge |
| "Who invented the telephone?" | History fact |
| "How tall is the Eiffel Tower?" | Measurement fact |
| "What's the capital of Australia?" | Geography |
| "How many bones are in the human body?" | Science fact |
| "What does [word] mean?" | Dictionary |
| "How do you spell [word]?" | Spelling |
| "Translate '[phrase]' to Spanish" | Translation |
| "What's the population of Brazil?" | Statistics |
| "When did World War II end?" | Historical date |

---

## 🔚 Session Ending (English)

**Closing triggers:** "Goodbye" / "Stop" / "That's all" / "Thanks, that's it" / "See you later"

**Response:** "Goodbye! I'm here whenever you need me. 🔵"

---

## 🎙️ English Confirmation Phrases (Rotate these)

> "Done." / "Got it." / "All set." / "Sure thing!" / "Of course." / "There you go." / "Consider it done." / "On it!" / "Absolutely." / "Right away."
