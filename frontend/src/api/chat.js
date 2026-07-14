import request from './request'

// 流式对话（直接处理 fetch）
export function sendChatStream(message, callbacks) {
    const { onData, onDone, onError } = callbacks
    const token = localStorage.getItem('token')

    fetch('/api/chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({ message })
    })
        .then(async (response) => {
            if (!response.ok) {
                const errorText = await response.text()
                onError && onError(new Error(`请求失败 (${response.status}): ${errorText}`))
                return
            }
            const reader = response.body.getReader()
            const decoder = new TextDecoder()
            let buffer = ''

            while (true) {
                const { done, value } = await reader.read()
                if (done) break

                buffer += decoder.decode(value, { stream: true })
                const lines = buffer.split('\n')
                buffer = lines.pop()

                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        const data = line.replace(/^data:s?/, "").trim()
                        if (data === '[DONE]') {
                            onDone && onDone()
                            return
                        }
                        onData && onData(data)
                    }
                }
            }
        })
        .catch((err) => onError && onError(err))
}

// 获取对话历史
export function getHistory() {
    return request.get('/chat/history')
}

// 上传文件
export function uploadFile(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/chat/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

// 清除暂存文件
export function clearUploadedFile() {
    return request.delete('/chat/upload')
}

// 上传图片到 OSS
export function uploadImage(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/image/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}