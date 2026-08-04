<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useData, withBase } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

const VERSION = 'v1.7.1.2'
const REPO = 'https://github.com/OpenVdra/SmartSpawner'

const { lang, page } = useData()
const open = ref(false)
const root = ref(null)
const isVi = computed(() => (lang.value || '').startsWith('vi'))

const isChangelog = computed(() => page.value.relativePath.replace(/^vi\//, '') === 'docs/changelog.md')

const items = computed(() => [
  {
    icon: 'List',
    text: isVi.value ? 'Nhật ký thay đổi' : 'Changelog',
    href: withBase(isVi.value ? '/vi/docs/changelog' : '/docs/changelog'),
    external: false,
    active: isChangelog.value
  },
  { icon: 'Tag', text: isVi.value ? 'Bản phát hành' : 'Releases', href: `${REPO}/releases`, external: true },
  { icon: 'Bug', text: isVi.value ? 'Báo lỗi' : 'Report a bug', href: `${REPO}/issues/new/choose`, external: true }
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
  <div ref="root" class="version-dropdown" :class="{ open }">
    <button
      class="version-button"
      type="button"
      :aria-expanded="open"
      aria-haspopup="menu"
      @click="open = !open"
    >
      <span>{{ VERSION }}</span>
      <LucideIcon class="version-caret" name="ChevronDown" :size="14" />
    </button>

    <Transition name="version-menu">
      <div v-if="open" class="version-menu" role="menu">
        <a
          v-for="item in items"
          :key="item.text"
          class="version-item"
          :class="{ active: item.active }"
          role="menuitem"
          :href="item.href"
          :aria-current="item.active ? 'page' : undefined"
          :target="item.external ? '_blank' : undefined"
          :rel="item.external ? 'noreferrer' : undefined"
          @click="close"
        >
          <LucideIcon :name="item.icon" :size="15" />
          <span>{{ item.text }}</span>
          <LucideIcon v-if="item.external" class="external-icon" name="ArrowUpRight" :size="14" />
        </a>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.version-dropdown {
  position: relative;
  display: inline-flex;
  align-items: center;
  align-self: stretch;
  height: var(--vp-nav-height);
  margin: 0 4px;
}

.version-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--vp-c-text-1);
  font: 600 0.875rem/1 inherit;
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s;
}

.version-button:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-default-soft);
}

.version-caret {
  opacity: 0.7;
  transition: transform 0.22s ease;
}

.open .version-caret {
  transform: rotate(180deg);
}

.version-menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 100;
  min-width: 184px;
  padding: 6px;
  background-color: color-mix(in srgb, var(--vp-c-bg) 80%, transparent);
  backdrop-filter: blur(12px) saturate(150%);
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.22);
}

.version-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 12px;
  border-radius: 8px;
  color: var(--vp-c-text-1);
  font-size: 0.875rem;
  font-weight: 500;
  text-decoration: none !important;
}

.version-item:hover,
.version-item.active {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-brand-soft);
}

.version-item span {
  flex: 1;
}

.external-icon {
  opacity: 0.55;
}

.version-menu-enter-active {
  animation: version-menu-in 0.2s ease-out;
}

.version-menu-leave-active {
  animation: version-menu-in 0.15s ease-in reverse;
}

@keyframes version-menu-in {
  from {
    opacity: 0;
    transform: translateY(-8px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
