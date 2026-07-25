<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

const { page, theme } = useData()

const editHref = computed(() => {
  const pattern = theme.value.editLink?.pattern
  const path = page.value.filePath
  if (!pattern || !path) return null
  return typeof pattern === 'function'
    ? pattern(page.value)
    : pattern.replace(/:path/g, path)
})

const links = computed(() => {
  const items = []
  if (editHref.value) {
    items.push({ icon: 'Pencil', text: 'Edit this page on GitHub', href: editHref.value })
  }
  items.push(
    { icon: 'Star', text: 'Star on GitHub', href: 'https://github.com/OpenVdra/SmartSpawner' },
    { icon: 'MessageCircle', text: 'Chat on Discord', href: 'https://discord.gg/zrnyG4CuuT' },
    { icon: 'Heart', text: 'Support the project', href: 'https://ko-fi.com/openvdra' }
  )
  return items
})
</script>

<template>
  <div class="aside-links">
    <a
      v-for="link in links"
      :key="link.text"
      class="aside-link"
      :href="link.href"
      target="_blank"
      rel="noreferrer"
    >
      <LucideIcon :name="link.icon" :size="16" />
      <span>{{ link.text }}</span>
    </a>
  </div>
</template>

<style scoped>
.aside-links {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--vp-c-divider);
}

.aside-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 10px;
  font-size: 0.8rem;
  font-weight: 500;
  line-height: 1.3;
  color: var(--vp-c-text-2);
  text-decoration: none !important;
  transition: color 0.25s, background-color 0.25s;
}

.aside-link:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-brand-soft);
}

.aside-link svg {
  flex-shrink: 0;
  color: var(--vp-c-brand-1);
}
</style>
