# Car Setup and Tuning Mechanism Spec

## Purpose

- What should setup tuning let players express or optimize?

  Adjust engine/chassis/aero parameters for different feelings

- Should tuning be arcade-simple, sim-inspired, or somewhere between?

  Simulation physics

## Tunable Values

For each value, define range, effect, UI label, and downside.

| Value | Range | Positive Effect | Tradeoff | Notes |
| --- | --- | --- | --- | --- |
| Power Mode | Locked at Mode 1 | Fixed baseline output | Fuel-use tradeoff is not implemented yet | Not adjustable until the fuel system exists |
| Tyre Setup | C1-C5 | Higher compound number gives higher grip | Higher compound number wears faster | Not tunable, once made a tyre cannot be changed into other types |
| Front Wing | 3-7 degrees | More front downforce and sharper high-speed turn-in | More drag; too much front balance makes the rear less stable | Stronger influence on steering balance |
| Rear Wing | 9-15 degrees | More rear downforce and high-speed stability | More drag and less top speed | Stronger influence on total drag |
| Anti-roll Balance | 0-10 | Higher values transfer a larger share of cornering load through the front axle | High values promote understeer; low values promote oversteer | Continuous slider, 5 is neutral |
| Gearing | Preset 0-2 | Higher the mode, higher top speed | Higher the mode, higher ratio gives slower acceleration | Probably will turn into continuous ratio tuning later |
| Front Brake Bias | 50-65% | Higher values make braking more stable | Too much front bias increases front lockup/understeer; low bias destabilizes the rear | Continuous slider |

## UI Flow

- Where should tuning happen?

  On the dedicated car setup station. Insert one completed car, adjust readable sliders, review the predicted results, then press Apply Setup to begin one atomic setup operation.

## Costs and Limits

- Should tuning be free, consume parts, require time, or require track testing?

  Tuning is free for MVP, but later there might be like furnace tuning effective time cost

- Can players change setup anywhere?

  Anywhere with the assembly block for now, but might not be later

- Should setup changes persist on the item and spawned entity?

  Yes

## Feedback

- How should players understand the effect of setup changes?

  They will see through the HUD and driving experience feedback, like you can feel like the car is not accelerating that fast or reaches a top speed earlier or later

- Should the HUD show setup values?

  Yes, but on the left side or right side, not blocking primary HUD

- Should tooltips describe tradeoffs in plain language?

  There should be a handbook saying so

## Acceptance Criteria

- What must be implemented for setup tuning to become a real gameplay loop?

  The setup station has a dedicated layout rather than reusing the construction panel. Front wing, rear wing, anti-roll balance, final drive, and brake bias are readable sliders; power mode is visibly locked at 1 until fuel consumption provides a real tradeoff. One combined draft model predicts acceleration, top speed, aero grip, drag, and the resulting oversteer/understeer balance using a continuous blue-green-red scale. Aero balance compares front and rear wing, while the final handling balance also includes anti-roll and brake bias. A white marker shows the currently fitted value separately from the proposed slider value. Moving sliders is only a preview; the complete setup is applied after pressing Apply Setup. Tyre compound is not a free setup value: it comes from the fitted tyre item.
