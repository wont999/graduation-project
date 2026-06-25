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
import {ref, onMounted, watch} from 'vue'
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

watch(() => props.visible, (newVal) => {
  if (newVal && props.mode === 'list') {
    loadProcedures()
  }
})
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: var(--surface-card);
  border-radius: var(--radius-xl);
  padding: 28px;
  min-width: 420px;
  max-width: 520px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--gray-100);
}

.dialog h3 {
  margin: 0 0 20px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--gray-800);
}

.dialog-content label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--gray-600);
}

.dialog-content input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius-sm);
  margin-bottom: 14px;
  font-size: 14px;
  box-sizing: border-box;
  transition: all var(--transition);
  background: var(--gray-50);
}

.dialog-content input:focus {
  outline: none;
  border-color: var(--primary-400);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  background: white;
}

.dialog-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 20px;
}

.dialog-actions button {
  padding: 10px 18px;
  border-radius: var(--radius-sm);
  border: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all var(--transition);
}

.btn-save {
  background: linear-gradient(135deg, var(--primary-500), var(--primary-600));
  color: white;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.3);
}

.btn-save:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--primary-600), var(--primary-700));
  box-shadow: 0 4px 10px rgba(37, 99, 235, 0.4);
  transform: translateY(-1px);
}

.btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.btn-cancel {
  background: var(--gray-100);
  color: var(--gray-600);
}

.btn-cancel:hover {
  background: var(--gray-200);
}

.btn-load {
  background: var(--primary-500);
  color: white;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
}

.btn-load:hover {
  background: var(--primary-600);
}

.btn-delete {
  background: var(--error);
  color: white;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
}

.btn-delete:hover {
  background: #DC2626;
}

.procedure-list {
  max-height: 300px;
  overflow-y: auto;
}

.procedure-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--gray-100);
}

.procedure-item:last-child {
  border-bottom: none;
}

.procedure-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.procedure-info strong {
  font-size: 14px;
  color: var(--gray-700);
}

.procedure-info small {
  color: var(--gray-400);
  font-size: 12px;
}

.procedure-actions {
  display: flex;
  gap: 6px;
}

.loading, .empty {
  text-align: center;
  padding: 32px;
  color: var(--gray-400);
  font-size: 14px;
}
</style>