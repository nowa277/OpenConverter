function rubberOffset(dx, actionWidth, rubber = 0.55) {
  if (dx >= 0) return (dx * actionWidth * rubber) / (actionWidth + rubber * dx) * -1 === 0 ? 0 : dx * 0.25;
  const over = Math.min(0, dx + actionWidth);
  if (dx >= -actionWidth) return dx;
  return -actionWidth + (over * actionWidth * rubber) / (actionWidth + rubber * Math.abs(over));
}

function snapTarget(x, actionWidth) {
  return x <= -actionWidth / 2 ? -actionWidth : 0;
}

function shouldCollapse(x, width) {
  return x <= -width * 0.72;
}

module.exports = { rubberOffset, snapTarget, shouldCollapse };
