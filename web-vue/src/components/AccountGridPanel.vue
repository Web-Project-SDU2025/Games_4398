<template>
  <section class="account-grid-page">
    <div class="account-grid-content">
      <h2 class="account-grid-title">
        <span class="account-grid-icon" :class="icon" aria-hidden="true"></span>
        {{ title }}
      </h2>
      <slot />
    </div>
  </section>
</template>

<script setup>
defineProps({ title: String, icon: String });
</script>

<style>
.account-grid-content {
  max-width: 480px;
  min-width: 0;
}

.account-grid-title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin: 0 0 var(--space-lg);
  font-size: 24px;
  font-weight: 700;
}

.account-grid-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  color: var(--color-muted);
}

.account-grid-form {
  display: grid;
  gap: var(--space-md);
}

.account-grid-page .account-grid-form label {
  display: grid;
  gap: var(--space-sm);
  min-width: 0;
  color: var(--color-ink);
}

.account-grid-page .account-grid-form :is(input, textarea) {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 44px;
  padding: var(--space-sm) 12px;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-control);
  background: var(--color-field);
  color: var(--color-ink);
  font: inherit;
}

.account-grid-page .account-grid-form textarea {
  min-height: 88px;
  resize: vertical;
}

.account-grid-page .account-grid-form input[readonly] {
  color: var(--color-muted);
}

.account-grid-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.account-grid-page .account-grid-actions button {
  min-height: 44px;
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-control);
  background: var(--color-paper-3);
  color: var(--color-ink);
}

.account-grid-page .account-grid-actions button[type="submit"] {
  background: var(--color-play-action);
  color: var(--color-play-action-ink);
  font-weight: 600;
}

.account-grid-page .account-grid-actions button:hover:not(:disabled) {
  border-color: var(--color-focus);
}

.account-grid-page .account-grid-actions button:disabled {
  opacity: 0.56;
}
</style>
