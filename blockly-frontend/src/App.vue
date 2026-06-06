<script setup>
import { ref, onMounted } from 'vue'
import BlocklyWorkspace from './components/BlocklyWorkspace.vue'
import ResultPanel from './components/ResultPanel.vue'
import SaveProcedureDialog from './components/SaveProcedureDialog.vue'
import { executeScript } from './api/procedures'
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
    result.value = await executeScript(generatedCode.value)
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
  <div style="display: flex; height: 100vh">
    <!-- Левая панель: Blockly -->
    <div style="flex: 2; padding: 10px; display: flex; flex-direction: column">
      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px">
        <h2 style="margin: 0">Blockly Constructor</h2>
        <button @click="openSaveDialog" class="toolbar-btn">Сохранить</button>
        <button @click="showListDialog = true" class="toolbar-btn">Мои процедуры</button>
      </div>
      <div style="flex: 1; min-height: 0">
        <BlocklyWorkspace @code-change="generatedCode = $event" />
      </div>
    </div>

    <!-- Центр: Generated Code -->
    <div style="flex: 1; padding: 10px; border-left: 1px solid #ccc; display: flex; flex-direction: column; overflow: hidden">
      <h2>Generated Code</h2>
      <pre style="background: #f5f5f5; padding: 10px; overflow: auto; flex: 1; font-family: monospace; font-size: 13px">{{ generatedCode }}</pre>
      <button
          @click="handleExecute"
          :disabled="isLoading"
          style="margin-top: 10px; padding: 10px 20px; color: white; border: none; border-radius: 4px; font-size: 14px"
          :style="{ background: isLoading ? '#999' : '#4CAF50', cursor: isLoading ? 'wait' : 'pointer' }"
      >
        {{ isLoading ? 'Executing...' : 'Execute' }}
      </button>
    </div>

    <!-- Правая панель: Result -->
    <div style="flex: 1; padding: 10px; border-left: 1px solid #ccc; overflow: auto">
      <h2>Result</h2>
      <ResultPanel :result="result" :is-loading="isLoading" :error="error" />
    </div>
  </div>

  <!-- Диалоги -->
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
.toolbar-btn {
  padding: 4px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 13px;
}
.toolbar-btn:hover {
  background: #e8e8e8;
}
</style>