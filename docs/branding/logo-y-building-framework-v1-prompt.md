# Y 教学楼 · 框架首稿

2026-10-20，使用内置 imagegen 生图模型；非代码绘图。参考用户上传的一粗一细框架手稿，以左侧细线版为结构依据；蓝色教学楼图为应避免的错误示例。

输出：[logo-y-building-framework-v1.png](logo-y-building-framework-v1.png)。本稿仅校准框架，不加窗户，尚未定稿，未接入应用；旧稿保留。

## 实际生成提示词

```text
Use case: logo-brand.
Create ONE original YOURoom logo framework based faithfully on the user's first reference image (the WHITE image showing a thin and a thick dark olive Y/cuboid sketch). The second image, the blue building with windows, is a REJECTED counterexample: do NOT follow its geometry.

Reference roles: first attachment = authoritative geometry, especially the LEFT thin-stroke version. Second attachment = wrong result to avoid.
Output: 1024x1024 white background, one centered standalone flat dark olive-black #292610 mark, no text, no comparison panel. Generous margins. Clean sharp vector-like geometry.

Critical topology: FOUR disconnected components only: one connected central Y, one top inverted-V chevron, one left bent side-outline, one right bent side-outline. These four components together suggest the nine visible edges of an isometric cuboid. The Y IS the front roof ridges and front vertical building corner; it is NOT a smaller Y floating inside an otherwise complete box.

Use the silhouette/proportions of the user's LEFT sketch, not the squat wide rejected building. A compact balanced cuboid, about 0.85 to 0.95 as wide as tall, not a tall tower. Three directions only: vertical, and two diagonals approximately 30 degrees above/below horizontal.

Detailed geometry:
1. Central Y has its junction around 46% of mark height. Its two diagonal arms run all the way outward/upward to the leftmost and rightmost roof-front corners (around 22% of mark height). Its vertical stem runs down to the lowest front vertex. All three strokes meet at ONE solid junction without a gap. Arm endpoints are at full building width; do NOT shorten the Y so it sits inside the box.
2. The separate top roof chevron runs from a point slightly inward/upward of the left Y tip to the topmost rear roof apex, then down/right to a point slightly inward/upward of the right Y tip. It ends SHORT of the Y tips at BOTH sides, leaving crisp visible gaps.
3. Left separate side-outline begins just BELOW the left Y tip with a small gap, runs vertically down to the rear-left bottom corner, then diagonally down/right towards the lowest front vertex; it stops SHORT of the Y stem's bottom tip.
4. Right separate side-outline mirrors this: start just BELOW right Y tip, descend vertically, turn diagonally down/left, stop SHORT of Y stem bottom tip.
At each of the THREE Y endpoints, BOTH adjoining peripheral lines stop before reaching the Y: six short air gaps total arranged around the three endpoint locations. This breaks the perimeter into three separate outer polylines. NEVER close the outer perimeter into a hexagon.

Stroke treatment: as in the thin left sketch, central Y is clearly heavier, approximately twice the peripheral stroke weight. Confident modest-width straight strokes, sharp mitered building corners, crisp oblique terminal cuts matching the reference, NO round pill end caps. White negative space fully separates the central Y from the three outer components. The Y reads first, cuboid second.

This pass establishes the correct building skeleton ONLY: do not add windows, doors, hatching, or any architectural details yet.
No secondary parallel borders beside Y, no inset roof diamond, no enclosing complete hexagon, no detached roof-floating small Y, no hidden edges, no extruded bevel faces, no perspective shading, no gradients, no glow, no grain, no shadow, no 3D material, no gold metal, no watermark. Make it a faithful polished version of the user's hand-drawn framework, not a new reinterpretation.
```
