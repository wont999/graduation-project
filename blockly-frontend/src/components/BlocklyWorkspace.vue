<script setup>
import {ref, onMounted, onUnmounted} from 'vue'
import * as Blockly from 'blockly'
import {javascriptGenerator} from 'blockly/javascript'
import {initCustomBlocks} from '../blockly/customBlocks'
import {toolbox} from '../blockly/toolbox'
import {fetchTables, fetchTableColumns} from '../api/tables'
import { refreshSavedProceduresCache } from '../blockly/customBlocks'
import { createTableCategories } from '../blockly/dataBlocks'

const emit = defineEmits(['code-change'])

const blocklyDiv = ref(null)
let workspace = null

onMounted(async () => {
  // Загрузка русской локали Blockly
  window.Blockly = Blockly
  const script = document.createElement('script')
  script.src = '/src/blockly/locales/ru.js'
  document.head.appendChild(script)

  initCustomBlocks()

  // Переопределяем text_print — в GraalVM нет window/alert
  javascriptGenerator.forBlock['text_print'] = function(block, generator) {
    const text = generator.valueToCode(block, 'TEXT', javascriptGenerator.ORDER_NONE) || "''"
    return text + ';\n'
  }

  // Загрузка таблиц с бэкенда
  try {
    const tables = await fetchTables()
    if (tables && tables.length > 0) {
      const tableData = {}
      for (const table of tables) {
        const columns = await fetchTableColumns(table.table_name)
        tableData[table.table_name] = columns
      }
      const dataCategory = createTableCategories(tableData)
      const actionIdx = toolbox.contents.findIndex(c => c.name === 'Действия')
      if (actionIdx >= 0) {
        toolbox.contents.splice(actionIdx, 0, dataCategory)
      } else {
        toolbox.contents.push(dataCategory)
      }
    }
  } catch (e) {
    console.error('Failed to load tables:', e)
  }

  if (!blocklyDiv.value) return

  workspace = Blockly.inject(blocklyDiv.value, {
    toolbox,
    grid: {spacing: 20, length: 3, colour: '#ccc', snap: true},
    zoom: {controls: true, wheel: true, startScale: 1.0},
  })

  window.__workspace = workspace

  refreshSavedProceduresCache(workspace)

  workspace.addChangeListener(() => {
    try {
      if (!workspace) return
      const code = javascriptGenerator.workspaceToCode(workspace)
      emit('code-change', code)
    } catch (e) {
      console.error('Error in handleChange:', e)
    }
  })
})

onUnmounted(() => {
  if (workspace) {
    workspace.dispose()
    workspace = null
  }
})
</script>

<template>
  <div ref="blocklyDiv" style="height: 100%; width: 100%"></div>
</template>
