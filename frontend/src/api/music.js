import request from './request'

// 生成简谱旋律
export function generateMusic(params) {
    return request.post('/music/generate', params)
}
