# Phone test checklist (nothing below has been compiled or run yet)

Build first: Android Studio, Sync Gradle (new CameraX dependencies need internet once), Build > Make Project. Send the first errors.

## Languages
- [ ] Language row scrolls; non-English/Hindi chips say "(beta)".
- [ ] Switch EN, HI, BN, UR: home, result, questions, organic card, cause names change. Urdu mirrors the layout.
- [ ] Tamil/Kannada/Odia/Malayalam/Punjabi/Assamese: gaps show English (expected, translation unfinished).
- [ ] Ask a dose question in Bengali: answer is in Bengali with 1800-180-1551.

## Questions
- [ ] "Check to be sure": after a leader reaches 60%, follow-up questions continue; green "Following: ..." line; stops by 14 questions.

## New cards
- [ ] Home: Camera with live tips (permission prompt, tips change in dark/blur, Take photo opens the result).
- [ ] Home: Weather watch, Crop stage guide, Yellow leaves check, Help (dial buttons open the dialler only), Season diary (notes save, Export shares CSV).
- [ ] Result (diseased leaf): Look-alike card, Spray or wait (chips change the verdict, reminder button), cost card, share card (WhatsApp), Friends card (pests only), "Try in this order" before organic then chemicals.
- [ ] Reminder: set 2-day reminder, change phone date, banner appears on Home.
- [ ] Simple mode switch: big sign, name, verdict, Listen button at top of result.
- [ ] Plot: Share plot report image, 4-day reminder.

## Home redesign
- [ ] Home shows: language, crop, reminders, Take photo, Gallery, a grid of 9 tools, last 3 checks, tips.
- [ ] Each tile opens: live camera, plot, library, diary, weather, stage guide, yellow-leaves check, help, settings.
- [ ] Settings dialog: state, big text, simple mode, Smart download card, About, clear/export history.
- [ ] All chips are at least 44dp tall and small grey notes are readable outdoors.

## Automated
- [ ] Run: Gradle `testDebugUnitTest` (LogicTest: spray-or-wait rules, translation key hash).

## Known limits
- Weather/stage cards are keyword matches on the checked notes, not forecasts.
- Tamil, Kannada, Odia, Malayalam, Punjabi, Assamese translations incomplete; all non-Hindi translations are machine-made.
- New card text is machine-translated into all 11 languages (beta); it still needs a native check.

## Did it work? / ask a person / reminders (added 9 Oct 2026)
- [ ] Save a diseased check, tap "Remind me", wait for it (or set the phone date forward): a notification arrives and the Home banner shows
- [ ] On the banner tap Better, Same, Worse: the reminder disappears; Worse shows the red "ask a person" card
- [ ] "Call Kisan Call Centre" opens the dialer with 1800-180-1551 (nothing is dialled by itself)
- [ ] "Send photo and result to an expert" opens the share sheet with the card
- [ ] Re-check the same problem with more damage (5+ points): the red card shows on the result screen. Severe damage also shows it
- [ ] After 3 answers for one problem, "Your re-checks that got better: N%" shows on that problem's result
- [ ] Set a reminder, restart the phone: the reminder still fires (and one that came due while off shows at boot)
- [ ] Change the language to Odia / Tamil / Urdu: buttons, card and notification text are in that language (Urdu is right-to-left)
- [ ] Diary > Export: the CSV has a second section with Better/Same/Worse answers

## Voice commands and new look (added 11 Oct 2026)
- [ ] Mic button (green circle, top right of Home and Result): say "take photo", "help", "diary", "weather" in English. The matching screen opens
- [ ] Change language to Hindi, Tamil, Urdu: say the same in that language ("फोटो लें", "உதவி"). If voice typing is missing for a language, a message says so
- [ ] Result screen: say "listen" (reads the advice), "new check" or "back" (goes home), "call" (opens the dialler)
- [ ] Take photo asks for camera permission once, then opens the camera without closing the app
- [ ] Weather watch and Crop stages open with the crop of your latest check (no "pick your crop" text)
- [ ] Healthy random leaf should not be called a pest
- [ ] Mic asks for microphone permission (in the app, not a system "voice search" box); say a command; it works. Deny permission: a clear message and a list of actions opens
- [ ] If voice typing is missing for a language (Odia, Assamese), the message says how to install it and the action list lets you do the same thing by tapping
- [ ] "Speak your question" in Ask uses the same mic and fills the question box
