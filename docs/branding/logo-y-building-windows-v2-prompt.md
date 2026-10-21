# Y 教学楼 · 四窗细化稿

2026-10-20，使用内置 imagegen 生图模型，对已确认的[框架首稿](logo-y-building-framework-v1.png)作局部编辑。保留 Y、比例、外缘三段与六处空隙，仅在两立面各增加两扇窗。单色空心窗顺应轴测方向，屋顶留白。

输出：[logo-y-building-windows-v2.png](logo-y-building-windows-v2.png)。首稿与旧方案均保留；本稿待确认，未替换正式应用图标。

## 实际编辑提示词

```text
Use case: precise-object-edit / logo-brand.
Edit the provided APPROVED YOURoom logo framework. The provided image is the edit target, NOT a loose style reference.

Primary request: preserve the entire existing skeleton and add only FOUR small outlined classroom windows, TWO on each visible vertical facade, making the abstract mark read as a teaching building without obscuring the Y.

LOCKED invariants: keep EXACTLY the current silhouette, width/height ratio, placement, view angle, all existing stroke widths, dark olive-black ink, white background, sharp corners, oblique terminal cuts, Y junction, and EVERY existing gap. Do not rescale, squat, stretch, round, reconnect, recolor, bevel or otherwise redesign any existing line.
The Y itself is the roof-front ridges and nearest vertical building corner. Its two arms extend to the full building width. Its lower stem reaches the lowest front vertex.
The roof-back chevron and two side polylines remain THREE disconnected outer components, separated from Y by the six existing small white air gaps. NEVER complete a closed outer hexagon. NEVER add a smaller floating Y or a second border around it.

ONLY additions:
On each white side facade add TWO small identical outlined parallelogram windows, one above the other, four total. Use coherent mirrored placement between left/right facades, centered within their respective face areas, with comfortable margins away from Y and outer edges.
Each window is a SIMPLE hollow four-sided frame with a white center, NO internal grid, NO mullions, NO detail inside. Vertical edges parallel the building's verticals; top and bottom edges follow that face's diagonal isometric direction, NOT screen-horizontal. The two stacked windows on each face have the same size and balanced vertical separation.
Window stroke is approximately half the peripheral-outline weight, clearly secondary to the heavy Y. Window width around one third of the visible face width; compact and generous whitespace, not giant hollow panels. Use exactly the same flat dark olive-black as existing strokes.
Keep the entire roof area completely empty. Do NOT add doors, clock, books, steps, base plinth, extra floors, brickwork, letterforms or text.
Make the first reading a bold Y, the second reading a clean teaching-building icon, not a warehouse or detailed illustration.
Output one centered icon on the existing square white canvas, preserving the original framing and margins. Crisp, uniform, flat vector-like solid ink; no gradient, grain, texture, metallic light, shadow, glow, 3D volume, mockup, title or watermark.
```
