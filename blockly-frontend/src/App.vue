<script setup>
import { ref, onMounted } from 'vue'
import BlocklyWorkspace from './components/BlocklyWorkspace.vue'
import ResultPanel from './components/ResultPanel.vue'
import SaveProcedureDialog from './components/SaveProcedureDialog.vue'
import { executeScriptAsync } from './api/procedures'
import { logout } from './api/auth'
import * as Blockly from 'blockly'
import { javascriptGenerator } from 'blockly/javascript'

const generatedCode = ref('// Код появится после добавления блоков')
const isLoading = ref(false)
const result = ref(null)
const error = ref(null)

const showSaveDialog = ref(false)
const showListDialog = ref(false)
const currentXml = ref('')
const currentJs = ref('')

const handleExecute = async () => {
  if (!generatedCode.value || generatedCode.value.trim() === '' || generatedCode.value.includes('// Код появится')) {
    alert('Добавьте блоки на workspace')
    return
  }
  isLoading.value = true
  result.value = null
  error.value = null
  try {
    result.value = await executeScriptAsync(generatedCode.value)
  } catch (err) {
    error.value = err
  } finally {
    isLoading.value = false
  }
}

function openSaveDialog() {
  try {
    if (window.__workspace) {
      const xmlDom = Blockly.Xml.workspaceToDom(window.__workspace)
      currentXml.value = Blockly.Xml.domToText(xmlDom)
      currentJs.value = javascriptGenerator.workspaceToCode(window.__workspace)
    }
  } catch (e) {
    console.error('Error serializing workspace:', e)
  }
  showSaveDialog.value = true
}

function onProcedureSaved() {
  import('./blockly/customBlocks').then(({ refreshSavedProceduresCache }) => {
    if (window.__workspace) refreshSavedProceduresCache(window.__workspace)
  })
}

function onProcedureLoad(procedure) {
  showListDialog.value = false
  if (procedure.blocklyXml && window.__workspace) {
    const xml = Blockly.utils.xml.textToDom(procedure.blocklyXml)
    Blockly.Xml.clearWorkspaceAndLoadFromXml(xml, window.__workspace)
  }
}
</script>

<template>
  <div class="app-layout">
    <!-- Header -->
    <header class="app-header">
      <div class="header-left">
        <div class="logo">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <rect x="2" y="2" width="9" height="9" rx="2" fill="#3B82F6"/>
            <rect x="13" y="2" width="9" height="9" rx="2" fill="#60A5FA"/>
            <rect x="2" y="13" width="9" height="9" rx="2" fill="#60A5FA"/>
            <rect x="13" y="13" width="9" height="9" rx="2" fill="#93C5FD"/>
          </svg>
          <span class="logo-text">Blockly Constructor</span>
        </div>
        <div class="header-actions">
          <button @click="openSaveDialog" class="btn btn-outline">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
              <polyline points="17,21 17,13 7,13 7,21"/>
              <polyline points="7,3 7,8 15,8"/>
            </svg>
            Сохранить
          </button>
          <button @click="showListDialog = true" class="btn btn-outline">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
            Мои процедуры
          </button>
        </div>
      </div>
      <div class="header-right">
        <button @click="logout" class="btn btn-logout">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16,17 21,12 16,7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          Выйти
        </button>
      </div>
    </header>

    <!-- Main Content -->
    <div class="app-content">
      <!-- Left: Blockly -->
      <div class="panel panel-workspace">
        <BlocklyWorkspace @code-change="generatedCode = $event" />
      </div>

      <!-- Center: Code -->
      <div class="panel panel-code">
        <div class="panel-header">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="16,18 22,12 16,6"/>
            <polyline points="8,6 2,12 8,18"/>
          </svg>
          Generated Code
        </div>
        <pre class="code-block">{{ generatedCode }}</pre>
        <button
          @click="handleExecute"
          :disabled="isLoading"
          class="btn btn-execute"
          :class="{ 'btn-loading': isLoading }"
        >
          <svg v-if="!isLoading" width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <polygon points="5,3 19,12 5,21"/>
          </svg>
          <span v-if="isLoading" class="spinner"></span>
          {{ isLoading ? 'Выполнение...' : 'Выполнить' }}
        </button>
      </div>

      <!-- Right: Result -->
      <div class="panel panel-result">
        <div class="panel-header">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14,2 14,8 20,8"/>
          </svg>
          Результат
        </div>
        <ResultPanel :result="result" :is-loading="isLoading" :error="error" />
      </div>
    </div>
  </div>

  <!-- Dialogs -->
  <SaveProcedureDialog
    :visible="showSaveDialog"
    mode="save"
    :currentXml="currentXml"
    :currentJs="currentJs"
    @close="showSaveDialog = false"
    @saved="onProcedureSaved"
  />
  <SaveProcedureDialog
    :visible="showListDialog"
    mode="list"
    @close="showListDialog = false"
    @load="onProcedureLoad"
  />
</template>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--surface-bg);
}

/* Header */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;
  background: var(--surface-card);
  border-bottom: 1px solid var(--gray-200);
  box-shadow: var(--shadow-sm);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-800);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-right {
  display: flex;
  align-items: center;
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition);
  border: none;
  white-space: nowrap;
}

.btn-outline {
  background: var(--surface-card);
  color: var(--gray-600);
  border: 1px solid var(--gray-200);
}

.btn-outline:hover {
  background: var(--primary-50);
  border-color: var(--primary-300);
  color: var(--primary-600);
}

.btn-execute {
  width: 100%;
  padding: 12px 20px;
  background: linear-gradient(135deg, var(--primary-500), var(--primary-600));
  color: white;
  font-size: 14px;
  font-weight: 600;
  border-radius: var(--radius-md);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
  margin-top: 12px;
}

.btn-execute:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--primary-600), var(--primary-700));
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.4);
  transform: translateY(-1px);
}

.btn-execute:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.btn-logout {
  background: none;
  color: var(--gray-500);
  border: 1px solid var(--gray-200);
}

.btn-logout:hover {
  color: #ef4444;
  border-color: #fecaca;
  background: #fef2f2;
}

.btn-loading {
  background: var(--gray-400);
  box-shadow: none;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Content */
.app-content {
  display: flex;
  flex: 1;
  gap: 12px;
  padding: 12px;
  overflow: hidden;
}

/* Panels */
.panel {
  background: var(--surface-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-workspace {
  flex: 2;
}

.panel-code {
  flex: 1;
  min-width: 280px;
}

.panel-result {
  flex: 1;
  min-width: 260px;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-600);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--gray-100);
  background: var(--gray-50);
}

.panel-header svg {
  color: var(--primary-500);
}

/* Code block */
.code-block {
  flex: 1;
  margin: 12px;
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
}

.panel-code .btn-execute {
  margin: 0 12px 12px;
  width: calc(100% - 24px);
}
</style>
