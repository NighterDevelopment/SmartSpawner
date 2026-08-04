<script setup>
import { computed } from 'vue'
import { withBase } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

const props = defineProps({
  title: String,
  icon: String,
  image: String,
  alt: String,
  link: String,
  action: {
    type: String,
    default: 'Explore feature'
  }
})

const resolvedLink = computed(() => {
  if (!props.link) return ''
  return props.link.startsWith('/') ? withBase(props.link) : props.link
})
</script>

<template>
  <article class="feature-media-card">
    <a
      v-if="resolvedLink"
      class="fmc-media"
      :href="resolvedLink"
      :aria-label="`${title}: ${action}`"
    >
      <img :src="image" :alt="alt || title" loading="lazy" decoding="async">
      <span class="fmc-source">Modrinth gallery</span>
    </a>
    <div v-else class="fmc-media">
      <img :src="image" :alt="alt || title" loading="lazy" decoding="async">
      <span class="fmc-source">Modrinth gallery</span>
    </div>

    <div class="fmc-body">
      <div class="fmc-header">
        <span v-if="icon" class="fmc-icon" aria-hidden="true">
          <LucideIcon :name="icon" :size="18" />
        </span>
        <h2 class="fmc-title">{{ title }}</h2>
      </div>

      <div class="fmc-copy">
        <slot></slot>
      </div>

      <a v-if="resolvedLink" class="fmc-action" :href="resolvedLink">
        {{ action }}
        <LucideIcon name="ArrowRight" :size="16" />
      </a>
    </div>
  </article>
</template>

<style scoped>
.feature-media-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 14px;
  transition: border-color 0.25s, box-shadow 0.25s, transform 0.25s;
}

.feature-media-card:hover {
  border-color: var(--vp-c-brand-1);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  transform: translateY(-2px);
}

.fmc-media {
  position: relative;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 100%;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 20%, rgba(61, 177, 112, 0.15), transparent 48%),
    #15191d;
  border-bottom: 1px solid var(--vp-c-border);
}

a.fmc-media {
  text-decoration: none;
}

.fmc-media img {
  position: absolute;
  inset: 0;
  display: block;
  width: 100% !important;
  height: 100% !important;
  margin: 0;
  object-fit: contain;
  transition: transform 0.3s ease;
}

.feature-media-card:hover .fmc-media img {
  transform: scale(1.015);
}

.fmc-source {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 8px;
  color: rgba(255, 255, 255, 0.86);
  font-size: 0.68rem;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.02em;
  background: rgba(15, 18, 21, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  backdrop-filter: blur(6px);
}

.fmc-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 20px;
}

.fmc-header {
  display: flex;
  align-items: center;
  gap: 11px;
}

.fmc-icon {
  display: inline-flex;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
  border-radius: 9px;
}

.fmc-title {
  margin: 0 !important;
  padding: 0 !important;
  border: 0 !important;
  color: var(--vp-c-text-1);
  font-size: 1.05rem;
  font-weight: 650;
  line-height: 1.35;
}

.fmc-copy {
  flex: 1;
  margin-top: 12px;
  color: var(--vp-c-text-2);
  font-size: 0.9rem;
  line-height: 1.65;
}

.fmc-copy :deep(p) {
  margin: 0;
}

.fmc-copy :deep(ul) {
  margin: 8px 0 0;
}

.fmc-action {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 6px;
  margin-top: 16px;
  color: var(--vp-c-brand-1);
  font-size: 0.86rem;
  font-weight: 650;
  text-decoration: none;
}

.fmc-action:hover {
  color: var(--vp-c-brand-2);
}

@media (max-width: 640px) {
  .fmc-body {
    padding: 18px;
  }

  .fmc-source {
    top: 8px;
    right: 8px;
  }
}
</style>
