<script setup>
import LucideIcon from '../icon/LucideIcon.vue'

defineProps({
  title: String,
  icon: String
})
</script>

<template>
  <div class="feature-card">
    <div class="fc-header">
      <div v-if="icon" class="fc-icon">
        <LucideIcon :name="icon" :size="18" />
      </div>
      <h3 class="fc-title">{{ title }}</h3>
    </div>
    <div class="fc-content">
      <slot></slot>
    </div>
  </div>
</template>

<style scoped>
.feature-card {
  background-color: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  height: 100%;
  transition: border-color 0.25s, background-color 0.25s;
}

.feature-card:hover {
  border-color: var(--vp-c-brand-1);
}

.fc-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.fc-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
  flex-shrink: 0;
}

.fc-title {
  margin: 0 !important;
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--vp-c-text-1);
}

.fc-content {
  font-size: 0.9rem;
  color: var(--vp-c-text-2);
  line-height: 1.6;
  flex-grow: 1;
}

.fc-content :deep(p) {
  margin: 0;
}

.fc-content :deep(img) {
  border-radius: 10px;
  margin-top: 16px;
  border: 1px solid var(--vp-c-border);
  width: 100%;
  height: auto;
}
</style>
