<script setup>
const BSTATS_URL = 'https://bstats.org/plugin/bukkit/SmartSpawner'
const CHART_SRC = 'https://bstats.org/signatures/bukkit/SmartSpawner.svg'
</script>

<template>
  <section class="usage-stats">
    <div class="usage-inner">
      <h2 class="usage-title">Trusted in Production</h2>
      <p class="usage-sub">Servers around the world use SmartSpawner to keep large farms responsive.</p>

      <a class="usage-chart" :href="BSTATS_URL" target="_blank" rel="noopener noreferrer">
        <img :src="CHART_SRC" alt="SmartSpawner bStats charts" loading="lazy" />
      </a>

    </div>
  </section>
</template>

<style scoped>
.usage-stats {
  padding: var(--home-section-gap) 24px 0;
}

.usage-inner {
  max-width: 860px;
  margin: 0 auto;
  text-align: center;
}

.usage-title {
  margin: 0 0 10px;
  border: 0;
  color: var(--vp-c-text-1);
  font-size: 1.75rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.usage-sub {
  margin: 0 0 32px;
  color: var(--vp-c-text-2);
  font-size: 0.97rem;
  line-height: 1.7;
}

.usage-chart {
  display: block;
  max-width: 760px;
  margin: 0 auto;
  padding: 20px 22px;
  background: #ffffff;
  border: 1px solid var(--vp-c-border);
  border-radius: 16px;
  box-shadow: 0 6px 24px color-mix(in srgb, var(--vp-c-text-1) 8%, transparent);
}

.usage-chart img {
  display: block;
  width: 100%;
  height: auto;
}

</style>
