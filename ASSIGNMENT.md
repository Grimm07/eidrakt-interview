# Backend Engineer – Technical Assessment

Thank you for your interest in joining **Eidra**.

This technical assessment is designed to give us a foundation for a technical discussion during the interview process. It is not intended to be a pass/fail coding test, nor a production‑ready implementation.

We are interested in how you reason about backend problems, make trade‑offs, and communicate technical decisions.

---

## Overview

You will build a small backend service in **Kotlin (JVM)**, preferably using **Ktor**.

The scope is intentionally limited and should take approximately **2–3 hours** to complete.

The code you submit will be used as the basis for a **code review discussion**.

---

## Problem Statement

You are helping a client launch a new API‑based product.

To protect the service and prepare for future billing models, the client needs a way to **track and enforce API usage quotas per customer**.

Your task is to build a backend service that acts as an **API Usage & Quota Service**.

This service is responsible only for tracking usage and enforcing quotas.  
There is no downstream business API to implement.

---

## Behavior

Each API key has a usage quota, for example: 100 units per hour.

When a request is received:
- If enough quota remains, the request is accepted (200 OK)
- If the quota would be exceeded, the request is rejected (429 Too Many Requests or similar)
- Usage quotas automatically reset after the configured time window.
- API keys must be tracked independently.
- The service must behave correctly under concurrent requests.
- Invalid input (e.g. negative units, missing headers) should be handled gracefully.


## Constraints

The following constraints are intentional and reflect an MVP client delivery:

- Single service instance
- In‑memory storage is acceptable
- No database or external cache is required
- No authentication beyond the API key
- No frontend or UI

## Optional Considerations

You may choose to implement or simply reason about any of the following:

- Fixed vs. sliding time windows
- Returning remaining quota information in responses
- Logging or basic metrics
- Unit, integration, or API‑level tests
- Clock abstraction or time handling strategies
- Choosing what not to implement is part of the exercise.

## What We’re Looking For

We will use this submission as the basis for a technical discussion.

We are interested in:

- Clear, readable, and maintainable code
- Sensible abstractions without over‑engineering
- Thoughtful trade‑offs within the given constraints
- Awareness of concurrency and time‑based logic
- Understanding of testing strategies (unit, integration, end‑to‑end)

We are not looking for:
- A perfect or fully production‑ready solution
- Extensive infrastructure or frameworks
- Premature optimization or scalability

## Deliverables

Please provide:

- A working backend service implemented in Kotlin
- A short README section describing:
    - Your design choices
    - Trade‑offs made for this MVP
    - What you would change or add for a larger or higher‑traffic system
    - (Optional) Tests or example requests

## Code Review

Once this has been completed you will be scheduled for a discussion where you will walk us through your solution, explaining:

- What the code does
- Why you structured it the way you did
- What assumptions or limitations exist
- What risks or improvements you would highlight to a client

## Final Notes

This assessment is intended to support a technical conversation, not to evaluate how much you can build in a limited time.
We value clear reasoning, smart decision‑making, and thoughtful communication.
Thank you for taking the time to complete this exercise — we look forward to discussing it with you!

## All done?

⭐ Awesome, great job! ⭐ The next step will be for an Eidra developer to take a look at your submission.

Best way to submit would be to share your repo (if public) so that we can clone and install locally. You can email your repo link to neil.bertz@eidra.com

## Questions?

Don't hesistate to contact us if you have any questions! you can email your recruitment contact and they will be happy to help you out.