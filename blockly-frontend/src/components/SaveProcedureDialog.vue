<template>
  <div v-if="visible" class="dialog-overlay" @click.self="$emit('close')">
    <div class="dialog">
      <h3>{{ mode === 'save' ? 'Сохранить процедуру' : 'Мои процедуры' }}</h3>

      <!-- Save mode -->
      <div v-if="mode === 'save'" class="dialog-content">
        <label>Название</label>
        <input v-model="name" placeholder="my_procedure"/>
        <label>Описание</label>
        <input v-model="description" placeholder="Опционально"/>
        <div class="dialog-actions">
          <button @click="$emit('close')" class="btn-cancel">Отмена</button>
          <button @click="handleSave" class="btn-save" :disabled="!name.trim()">Сохранить</button>
        </div>
      </div>

      <!-- List mode -->
      <div v-if="mode === 'list'" class="dialog-content">
        <div v-if="loading" class="loading">Загрузка...</div>
        <div v-else-if="!procedures || procedures.length === 0" class="empty">Нет сохранённых процедур</div>
        <div v-else class="procedure-list">
          <div v-for="p in procedures" :key="p.name" class="procedure-item">
            <div class="procedure-info">
              <strong>{{ p.name }}</strong>
              <small v-if="p.description">{{ p.description }}</small>
            </div>
            <div class="procedure-actions">
              <button @click="$emit('load', p)" class="btn-load">Загрузить</button>
              <button @click="handleDelete(p)" class="btn-delete">Удалить</button>
            </div>
          </div>
        </div>
        <div class="dialog-actions">
          <button @click="$emit('close')" class="btn-cancel">Закрыть</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {saveProcedure, fetchSavedProcedures, deleteSavedProcedure} from '../api/savedProcedures'

const props = defineProps({
  visible: Boolean,
  mode: {type: String, default: 'list'}, // 'save' | 'list'
  currentXml: {type: String, default: ''},
  currentJs: {type: String, default: ''}
})

const emit = defineEmits(['close', 'load', 'saved'])

const name = ref('')
const description = ref('')
const procedures = ref([])
const loading = ref(false)

async function handleSave() {
  if (!name.value.trim()) return
  try {
    await saveProcedure({
      name: name.value.trim(),
      description: description.value.trim(),
      blocklyXml: props.currentXml,
      generatedJs: props.currentJs
    })
    emit('saved')
    emit('close')
  } catch (err) {
    alert('Ошибка сохранения: ' + (err.response?.data?.errorMessage || err.message))
  }
}

async function loadProcedures() {
  loading.value = true
  try {
    procedures.value = (await fetchSavedProcedures()) || []
  } catch (err) {
    console.error('Failed to load procedures:', err)
    procedures.value = []
  } finally {
    loading.value = false
  }
}

async function handleDelete(p) {
  if (!confirm(`Удалить "${p.name}"?`)) return
  try {
    await deleteSavedProcedure(p.name)
    await loadProcedures()
  } catch (err) {
    alert('Ошибка удаления: ' + (err.response?.data?.errorMessage || err.message))
  }
}

onMounted(() => {
  if (props.mode === 'list') loadProcedures()
})
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: white;
  border-radius: 8px;
  padding: 24px;
  min-width: 420px;
  max-width: 560px;
  max-height: 80vh;
  overflow-y: auto;
}

.dialog h3 {
  margin: 0 0 16px 0;
}

.dialog-content label {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
  color: #555;
}

.dialog-content input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 14px;
  box-sizing: border-box;
}

.dialog-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 16px;
}

.dialog-actions button {
  padding: 8px 16px;
  border-radius: 4px;
  border: 1px solid #ccc;
  cursor: pointer;
  font-size: 14px;
}

.btn-save {
  background: #0078D7;
  color: white;
  border-color: #0078D7;
}

.btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-cancel {
  background: #f5f5f5;
}

.btn-load {
  background: #0078D7;
  color: white;
  border: none;
  padding: 4px 12px;
  border-radius: 4px;
  cursor: pointer;
}

.btn-delete {
  background: #d32f2f;
  color: white;
  border: none;
  padding: 4px 12px;
  border-radius: 4px;
  cursor: pointer;
}

.procedure-list {
  max-height: 300px;
  overflow-y: auto;
}

.procedure-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
}

.procedure-info {
  display: flex;
  flex-direction: column;
}

.procedure-info small {
  color: #888;
  font-size: 12px;
}

.procedure-actions {
  display: flex;
  gap: 6px;
}

.loading, .empty {
  text-align: center;
  padding: 24px;
  color: #888;
}
</style>