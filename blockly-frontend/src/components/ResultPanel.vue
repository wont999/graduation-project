<script setup>
const props = defineProps({
  result: Object,
  isLoading: Boolean,
  error: Object,
})
</script>

<template>
  <div class="result-panel">
    <!-- Loading -->
    <div v-if="isLoading" class="result-state">
      <div class="loading-spinner">
        <div class="spinner-ring"></div>
      </div>
      <span class="state-text">Выполнение скрипта...</span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="result-state result-error">
      <div class="error-icon">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
      </div>
      <span class="state-text error-text">
        {{ error?.response?.data?.errorMessage || error?.message || 'Неизвестная ошибка' }}
      </span>
    </div>

    <!-- Empty state -->
    <div v-else-if="!result" class="result-state result-empty">
      <div class="empty-icon">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <polygon points="5,3 19,12 5,21"/>
        </svg>
      </div>
      <span class="state-text">Нажмите "Выполнить" для запуска</span>
    </div>

    <!-- Result -->
    <div v-else class="result-content">
      <div class="result-status" :class="result.success ? 'status-success' : 'status-error'">
        <svg v-if="result.success" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
          <polyline points="22,4 12,14.01 9,11.01"/>
        </svg>
        <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
        {{ result.success ? 'Успешно' : 'Ошибка' }}
      </div>

      <pre v-if="result.result !== undefined && result.result !== null" class="result-data">{{
        typeof result.result === 'string' ? result.result : JSON.stringify(result.result, null, 2)
      }}</pre>

      <div v-if="result.errorMessage" class="result-error-msg">
        {{ result.errorMessage }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.result-panel {
  flex: 1;
  padding: 12px;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.result-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 32px 16px;
  text-align: center;
}

.state-text {
  font-size: 13px;
  color: var(--gray-400);
}

/* Loading */
.loading-spinner {
  width: 40px;
  height: 40px;
  position: relative;
}

.spinner-ring {
  width: 100%;
  height: 100%;
  border: 3px solid var(--gray-100);
  border-top-color: var(--primary-500);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Error */
.result-error .error-icon {
  color: var(--error);
}

.error-text {
  color: var(--error);
  font-size: 13px;
}

/* Empty */
.result-empty .empty-icon {
  color: var(--gray-300);
}

/* Content */
.result-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
}

.status-success {
  background: var(--success-bg);
  color: var(--success);
}

.status-error {
  background: var(--error-bg);
  color: var(--error);
}

.result-data {
  flex: 1;
  padding: 14px;
  background: var(--surface-code);
  border-radius: var(--radius-md);
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.6;
  color: var(--gray-700);
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid var(--gray-100);
  max-height: 60vh;
}

.result-error-msg {
  padding: 10px 14px;
  background: var(--error-bg);
  color: var(--error);
  border-radius: var(--radius-sm);
  font-size: 13px;
  border: 1px solid rgba(239, 68, 68, 0.2);
}
</style>
