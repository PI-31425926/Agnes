// Node type definitions
// Each node defines: input (what it consumes), output (what it produces)
export const NODE_TYPES = {
  text_input:       { label: '文本输入',   category: 'text', icon: '📝', input: ['text'],        output: ['text'],      consumer: false },
  text_refine:      { label: '文本优化',   category: 'text', icon: '✨', input: ['text'],        output: ['text'],      consumer: false, hidden: true },
  text_chat:        { label: '文本对话',   category: 'text', icon: '💬', input: ['text'],        output: ['text'],      consumer: true },
  text_to_image:    { label: '文生图',     category: 'image',icon: '🖼️', input: ['text'],        output: ['image'],     consumer: false },
  image_to_image:   { label: '图生图',     category: 'image',icon: '🎨', input: ['image','text'],output: ['image'],     consumer: false },
  image_understand: { label: '图片理解',   category: 'image',icon: '👁️', input: ['image'],       output: ['text'],      consumer: false },
  text_to_video:    { label: '文生视频',   category: 'video',icon: '🎬', input: ['text'],        output: ['video'],     consumer: true },
  image_to_video:   { label: '图生视频',   category: 'video',icon: '📹', input: ['image','text'],output: ['video'],     consumer: true },
  keyframe_animation:{ label: '关键帧动画',category: 'video',icon: '🎞️', input: ['images','text'],output: ['video'],   consumer: true },
}

// Human-readable labels
export const LABELS = {
  text_input: '文本输入', text_refine: '文本优化', text_chat: '文本对话',
  text_to_image: '文生图', image_to_image: '图生图', image_understand: '图片理解',
  text_to_video: '文生视频', image_to_video: '图生视频', keyframe_animation: '关键帧动画',
}

const categoryOrder = ['text', 'image', 'video']
const nodeCategories = {}
for (const [type, info] of Object.entries(NODE_TYPES)) {
  if (info.hidden) continue
  if (!nodeCategories[info.category]) nodeCategories[info.category] = []
  nodeCategories[info.category].push({ type, ...info })
}

export const categories = categoryOrder.map(cat => ({
  id: cat,
  label: { text: '文本', image: '图像', video: '视频' }[cat] || cat,
  nodes: nodeCategories[cat] || []
}))
