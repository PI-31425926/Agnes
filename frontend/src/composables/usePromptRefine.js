import { ref } from 'vue'
import request from '../api/request.js'

const SYSTEM_PROMPTS = {
  text_to_image:
    '你是一个专业的图像提示词优化师。用户会给你一段描述，你需要将其扩展为适合文生图的详细提示词。' +
    '遵循结构：[主体] + [场景/环境] + [风格] + [光照] + [构图] + [质量要求]。' +
    '输出简洁、高信息密度的中文提示词，不要有多余解释。',

  image_to_image:
    '你是一个专业的图像编辑提示词优化师。用户会给你修改要求，你需要将其扩展为适合图生图的详细指令。' +
    '遵循结构：[改变要求] + [新风格/场景] + [需要添加或移除的元素] + [需要保留的元素]。' +
    '输出简洁的中文提示词，不要有多余解释。',

  text_to_video:
    '你是一个专业的视频提示词优化师。用户会给你一段描述，你需要将其扩展为适合文生视频的提示词。' +
    '遵循结构：[主体] + [动作] + [场景] + [镜头运动] + [光线] + [风格]。' +
    '输出简洁、生动的中文提示词，不要有多余解释。',

  image_to_video:
    '你是一个专业的视频提示词优化师。用户会给你一段描述，你需要将其扩展为适合图生视频的提示词。' +
    '遵循结构：[主体] + [动作] + [场景] + [镜头运动] + [光线] + [风格]。' +
    '输出简洁、生动的中文提示词，不要有多余解释。',

  keyframe_animation:
    '你是一个专业的关键帧动画提示词优化师。用户会给你动画描述，你需要将其扩展为适合关键帧动画的提示词。' +
    '清晰描述关键帧之间的过渡关系，保持角色身份一致，镜头角度稳定，动作自然流畅。' +
    '输出简洁的中文提示词，不要有多余解释。',

  default:
    '你是一个专业的提示词优化师。将用户的简短描述扩展为详细、生动、富有画面感的提示词。' +
    '只输出优化后的提示词，不要有多余解释。',
}

export function usePromptRefine() {
  const loading = ref(false)
  const error = ref(null)

  async function refinePrompt(nodeType, currentPrompt) {
    if (!currentPrompt || currentPrompt.trim().length === 0) {
      throw new Error('请先输入提示词内容')
    }

    loading.value = true
    error.value = null

    try {
      const res = await request.post('/prompts/refine', {
        type: nodeType,
        prompt: currentPrompt,
      })
      return res.refined_prompt
    } catch (e) {
      error.value = e.message || '优化失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  return { refinePrompt, loading, error }
}
