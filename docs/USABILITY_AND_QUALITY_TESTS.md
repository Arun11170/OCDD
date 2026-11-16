# Usability, reliability and advice-quality tests (for report sections 4, 6 and 7)

## A. Usability test (3 to 5 people; farmers or extension workers if possible)
Give each person the phone with the app open and 4 printed/on-screen leaf photos. Do not explain the app. Ask them to:
1. Take or choose a photo and tell you what the app says the problem is.
2. Find out how serious it is.
3. Find out what to do next.
4. Switch to their language and read the advice.
5. Try a photo that is not a plant; say what the app tells them to do.

Record per person: task done yes/no, seconds, where they hesitated, what they did not understand, would they trust it (1-5), language used.
Report: success rate per task, median time to the first advisory, top 3 problems, 2 short quotes.

## B. Reliability test
1. Fully charged phone, airplane mode, 50 scans in a row (mix of sample photos). Count crashes, "model did not load" messages, and note memory in metrics.csv (mem_mb should not keep growing).
2. Repeat with low storage (under 1 GB free) and with 15% battery. Write down what the app does.
3. Open the app after a force-stop; first scan time (cold start) vs later scans.

## C. Advice-quality test (the 39 advisories from Settings > Run benchmark (3 per language))
Copy advisories.txt to the PC (Hindi and any other language: have a native speaker score it). For each advisory score 0/1 on each point and total it:
- Names the right problem (same as FACTS).
- Does not add a chemical, dose or number that is not in FACTS.
- Steps are all from FACTS.
- Tells the farmer when to call an expert.
- Readable and in the right language (Hindi: have a native speaker score this).
Report: % of advisories with all points = 1, % blocked by the safety check (check=FAIL lines), 2 examples of a blocked answer and why it was blocked.
