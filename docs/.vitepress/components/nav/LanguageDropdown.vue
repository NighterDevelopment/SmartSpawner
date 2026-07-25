<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useData, withBase } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

defineProps({ screenMenu: Boolean })

const { lang, page } = useData()
const open = ref(false)
const root = ref(null)
const isVi = computed(() => (lang.value || '').startsWith('vi'))

const localizedPath = (locale) => {
  let path = page.value.relativePath || 'index.md'
  path = path.replace(/^vi\//, '').replace(/\.md$/, '').replace(/(^|\/)index$/, '$1')
  return withBase(`${locale === 'vi' ? '/vi' : ''}/${path}`.replace(/\/$/, '/') || '/')
}

const items = computed(() => [
  { flag: '🇺🇸', text: 'English', href: localizedPath('en'), active: !isVi.value },
  { flag: '🇻🇳', text: 'Tiếng Việt', href: localizedPath('vi'), active: isVi.value }
])

const close = () => { open.value = false }
const onDocumentClick = event => {
  if (root.value && !root.value.contains(event.target)) close()
}
const onKeydown = event => {
  if (event.key === 'Escape') close()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="root" class="language-dropdown" :class="{ open, 'screen-menu': screenMenu }">
    <button
      class="language-button"
      type="button"
      :aria-expanded="open"
      :aria-label="isVi ? 'Đổi ngôn ngữ' : 'Change language'"
      aria-haspopup="menu"
      @click="open = !open"
    >
      <LucideIcon name="Languages" :size="17" />
      <LucideIcon class="language-caret" name="ChevronDown" :size="14" />
    </button>

    <Transition name="language-menu">
      <div v-if="open" class="language-menu" role="menu">
        <a
          v-for="item in items"
          :key="item.text"
          class="language-item"
          :class="{ active: item.active }"
          role="menuitem"
          :aria-current="item.active ? 'page' : undefined"
          :href="item.href"
          @click="close"
        >
          <span class="language-label">
            <span class="language-flag" aria-hidden="true">{{ item.flag }}</span>
            <span>{{ item.text }}</span>
          </span>
          <LucideIcon v-if="item.active" name="Check" :size="15" />
        </a>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.language-dropdown {
  position: relative;
  display: inline-flex;
  align-items: center;
  align-self: stretch;
  height: var(--vp-nav-height);
  margin: 0 4px;
}

.language-dropdown.screen-menu {
  display: none;
}

.language-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 36px;
  padding: 0 10px;
  color: var(--vp-c-text-1);
  font-family: inherit;
  line-height: 1;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.language-button:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-default-soft);
}

.language-caret {
  opacity: 0.7;
  transition: transform 0.22s ease;
}

.open .language-caret {
  transform: rotate(180deg);
}

.language-menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 100;
  min-width: 168px;
  padding: 6px;
  background-color: color-mix(in srgb, var(--vp-c-bg) 80%, transparent);
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(12px) saturate(150%);
  -webkit-backdrop-filter: blur(12px) saturate(150%);
}

.language-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 12px;
  color: var(--vp-c-text-1);
  font-size: 0.875rem;
  font-weight: 500;
  text-decoration: none !important;
  border-radius: 8px;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.language-item:hover,
.language-item.active {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-brand-soft);
}

.language-label {
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.language-flag {
  font-size: 1rem;
  line-height: 1;
}

.language-menu-enter-active {
  animation: language-menu-in 0.2s ease-out;
}

.language-menu-leave-active {
  animation: language-menu-in 0.15s ease-in reverse;
}

@keyframes language-menu-in {
  from {
    opacity: 0;
    transform: translateY(-6px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
