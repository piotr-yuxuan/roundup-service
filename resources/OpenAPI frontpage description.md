This Starling round-up service helps Starling customers save
effortlessly by rounding up their weekly spending to the nearest pound
and transferring the difference into a savings goal.

Each week, the service calculates the total round-up amount from all
qualifying transactions. For example, if a customer spends:

- £4.35 → rounded up to £5.00 → £0.65 saved
- £5.20 → rounded up to £6.00 → £0.80 saved
- £0.87 → rounded up to £1.00 → £0.13 saved

The round-up for the week is £1.58, which is then added to the
customer's savings goal.

## Key Features

- Idempotent weekly trigger: Trigger a round-up for a given calendar
  week. If the round-up was already processed, the request is safely
  ignored.
- Savings goal integration: By default, round-ups are added to a
  dedicated savings goal (created automatically if needed). You can
  also specify a custom savings goal name.
- Secure API: Access is protected using a bearer token (JWT).

## Usage

1. Authenticate by providing your Starling API JWT token.
2. Choose the calendar week (year and week number) for which to
   calculate the round-up.
2. Call the `POST /api/v0/trigger-round-up endpoint`.
3. Optionally, provide a savings goal name; otherwise a default one is
   used.
4. Receive a confirmation of the round-up job execution.

This service acts as a convenient companion to the Starling Developer
API, enabling customers to build disciplined savings habits with
minimal effort.
