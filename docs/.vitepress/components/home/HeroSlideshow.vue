<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const slides = [
  {
    src: '/gui-showcase.gif',
    alt: 'SmartSpawner in-game management interface',
    label: 'Spawner GUI'
  },
  {
    src: '/spawner-stacking.gif',
    alt: 'Stacking multiple spawners into a single block',
    label: 'Spawner stacking'
  },
  {
    src: '/spawner-list.gif',
    alt: 'Browsing spawners across multiple worlds',
    label: 'Spawner browser'
  }
]

const activeSlide = ref(0)
let rotationTimer
let reduceMotion

function stopRotation() {
  if (rotationTimer) {
    window.clearInterval(rotationTimer)
    rotationTimer = undefined
  }
}

function startRotation() {
  stopRotation()

  if (reduceMotion?.matches) return

  rotationTimer = window.setInterval(() => {
    activeSlide.value = (activeSlide.value + 1) % slides.length
  }, 6000)
}

function selectSlide(index) {
  activeSlide.value = index
  startRotation()
}

onMounted(() => {
  reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  reduceMotion.addEventListener('change', startRotation)
  startRotation()
})

onBeforeUnmount(() => {
  stopRotation()
  reduceMotion?.removeEventListener('change', startRotation)
})
</script>

<template>
  <div
    class="hero-slideshow"
    role="region"
    aria-label="SmartSpawner feature showcase"
    @mouseenter="stopRotation"
    @mouseleave="startRotation"
    @focusin="stopRotation"
    @focusout="startRotation"
  >
    <div class="hero-slideshow-frame">
      <img
        v-for="(slide, index) in slides"
        :key="slide.src"
        :src="slide.src"
        :alt="slide.alt"
        class="hero-slideshow-image"
        :class="{ active: index === activeSlide }"
        :aria-hidden="index !== activeSlide"
      >
      <span class="hero-slideshow-label">{{ slides[activeSlide].label }}</span>
    </div>

    <div class="hero-slideshow-controls" aria-label="Choose a feature image">
      <button
        v-for="(slide, index) in slides"
        :key="slide.label"
        type="button"
        :class="{ active: index === activeSlide }"
        :aria-label="`Show ${slide.label}`"
        :aria-current="index === activeSlide ? 'true' : undefined"
        @click="selectSlide(index)"
      />
    </div>
  </div>
</template>
