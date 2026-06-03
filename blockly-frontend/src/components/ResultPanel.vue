<script setup>
const props = defineProps({
    result: Object,
    isLoading: Boolean,
    error: Object,
})
</script>

<template>
    <div v-if="isLoading" style="padding: 20px; color: #666">⏳ Executing...</div>

    <div v-else-if="error" style="padding: 20px; color: #c00">
        ❌ Error: {{ error?.response?.data?.errorMessage || error?.message || 'Unknown error' }}
    </div>

    <div v-else-if="!result" style="padding: 20px; color: #999">Нажмите "Execute" для запуска</div>

    <div v-else style="padding: 10px">
        <div style="margin-bottom: 10px">
            <strong>Success:</strong> {{ result.success ? '✅' : '❌' }}
        </div>
        <pre v-if="result.result !== undefined && result.result !== null"
            style="background: #f5f5f5; padding: 10px; overflow: auto; font-family: monospace; font-size: 13px; max-height: 70vh">{{ typeof result.result === 'string' ? result.result : JSON.stringify(result.result, null, 2) }}</pre>
        <div v-if="result.errorMessage" style="color: #c00">Error: {{ result.errorMessage }}</div>
    </div>
</template>
