<script setup lang="ts">
defineProps<{ erro?: string; aviso?: string }>()
</script>

<template>
  <p v-if="aviso" class="ok" role="status">{{ aviso }}</p>
  <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
</template>
