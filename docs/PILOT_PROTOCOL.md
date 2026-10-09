# Pilot protocol: Crop Advisor (4 weeks, one district)

## Goal
Learn, with real farmers and real fields: (1) is the app's diagnosis right, (2) does the advice lead to improvement, (3) do farmers understand and trust it, (4) which crops/problems fail.

## Who and where
- 20-30 farmers in one district, 2-3 crops from: tomato, chilli, okra, rice, groundnut (choose by the KVK's season).
- Recruit through a KVK or an FPO. Include at least 5 farmers with low literacy and 5 with phones of 4 GB RAM or less.
- Needs: a KVK officer (reviewer), one field coordinator, Android phones (arm64, Android 8+).

## Before the pilot (week 0)
1. Install the build on test phones; run `docs/TEST_CHECKLIST.md`.
2. KVK officer reviews the product list and advice for the pilot crops (`review/KVK_review_sheet.csv`) and signs off or corrects.
3. Native speakers of the pilot languages review `review/review_<lang>.csv`.
4. Fix the language(s) and crop picker per farmer.
5. Consent: explain in the local language that photos stay on the phone, that the app gives no doses, and that exports of the answers file are shared only if the farmer agrees.

## Training (30 minutes, in the field)
Take a photo; read the result; "Check to be sure" questions; "Spray or wait"; set a reminder; answer Better / Same / Worse; call the Kisan Call Centre; share the advice card.

## During the pilot
- **Week 1 check-in (visit or call):** can they take photos, is the result understood, any crashes. Fix blocking problems.
- **Weeks 1-4:** farmers check problems as they appear. For each check the app saves photo, result, damage %, and the answer to "Was this right?" and "Better / Same / Worse".
- **Weekly (coordinator):** for 10 random checks per week, compare the app's answer with the KVK officer's diagnosis from the photo (and visit the field if possible). Record: right / partly right / wrong.
- **Week 4:** collect each phone's exports (Diary > Export; history CSV) with the farmer's consent.

## Measures
| Measure | How |
|---|---|
| Diagnosis accuracy in real fields | KVK-labelled sample vs app answer, per crop |
| "Not sure" rate | share of checks where the app asks the tree questions / expert |
| Re-check follow-through | reminders set vs answered |
| Outcome | share of Better / Same / Worse per problem (3+ answers) |
| Expert calls | taps on Call / Send to expert; problems that ended with a KVK visit |
| Understanding | 5-question interview per farmer (below) |
| Reliability | crashes, slow phones, languages with missing words |

## Farmer interview (week 4, 10 minutes, local language)
1. Did the app tell you something you did not already know? Give one example.
2. Did you follow any field step? Which? Did it help?
3. Was any word or sentence unclear or wrong? (note it)
4. Did you buy any product because of the app? Did you read the label?
5. Would you use it next season? What one thing would you change?

## Safety rules for the pilot
- No dose or mixing advice is given by the app or the coordinators; refer to the label and the KVK officer.
- If a farmer reports crop loss or a wrong result, the KVK officer follows up the same week.
- Stop and fix at once if: a wrong product is suggested for a crop, a banned product appears, or a translation changes the meaning of a safety sentence.

## Success (suggested)
- >=85% of answered checks right per pilot crop against the KVK label (and no crop under 75%).
- >=70% of reminders answered; >=50% of "Worse" answers lead to a KVK contact.
- >=80% of interviewed farmers would use it again.

## After the pilot
Fix the worst 10 confusion pairs, retrain with the pilot photos (with consent), publish a one-page result, and decide on the next district.
