# Continuous High-Accuracy Recovery Design

## Problem

The 14 August trip shows a real tracking gap after the app recorded `STOP` at 21:30. The Android location history shows that the app's high-accuracy GPS request ended shortly afterward, and no later movement was recorded on the return trip to Hanoi.

The current service maps `MovementMode.IDLE` to `PRIORITY_BALANCED_POWER_ACCURACY`. On the tested Samsung device this allows the GPS provider to turn off after a stop. If Activity Recognition does not deliver a reliable vehicle-enter transition, balanced fixes may not contain enough speed or movement information to return the detector to `MOVING`.

## Decision

While tracking is enabled, `TrackingService` will request `PRIORITY_HIGH_ACCURACY` every 10 seconds in every movement mode:

- `IDLE`
- `MOVING`
- `STOP_CANDIDATE`

Activity Recognition remains a supplementary movement signal. It must not be required to reactivate high-accuracy GPS after a stop.

## Unchanged Behavior

- Location callbacks are monitored every 10 seconds.
- Moving routes persist a `PERIODIC` record every 2 minutes.
- `START`, `TEMP_STOP`, and `STOP` transition rules remain unchanged.
- Stationary periods do not create a database row every 2 minutes.
- Accuracy and age are retained for backend evaluation; fixes are not discarded by this change.
- Report scheduling, CSV generation, email delivery, and record delivery states remain unchanged.

## Trade-off

Continuous high-accuracy GPS increases battery consumption. Completeness is prioritized because the approved product requirement is continuous 10-second monitoring while tracking is enabled.

## Testing

Add a unit-testable location-priority policy and prove that all movement modes select `PRIORITY_HIGH_ACCURACY`. Existing movement-detector tests must continue to pass, demonstrating that persistence frequency and event semantics did not change.

On the connected Samsung device, verify after installing the new build that the foreground location request remains high accuracy when the detector reaches an idle/stopped state.
