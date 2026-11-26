import { createApp, ref, onMounted } from 'vue';
import template from './template.html?raw';

console.log('Initializing Vue app - start...');

const app = createApp({
  setup() {
    console.log('Vue app initialized');
    const assets = ref([]);
    const currentPage = ref(0);
    const totalPages = ref(0);
    const totalElements = ref(0);
    const pageSize = 50;
    const loading = ref(false);
    const importFolder = ref('');
    const importMessage = ref('');
    const error = ref('');
    const storagePath = ref('');
    const filters = ref({ type: '', year: '', month: '', orientation: '', location: '', q: '' });
    const selected = ref(null);

    async function reload() {
      await load(0);
    }


    async function load(page = 0) {
      loading.value = true;
      error.value = '';
      const params = new URLSearchParams();
      Object.entries(filters.value).forEach(([key, value]) => {
        if (value) params.set(key, value);
      });
      params.set('page', page);
      params.set('size', pageSize);
      console.log('Loading assets with params:', params.toString());
      try {
        const response = await fetch('/api/assets?' + params);
        if (!response.ok) throw new Error(await response.text());
        const result = await response.json();
        assets.value = result.content;
        currentPage.value = result.page;
        totalPages.value = result.totalPages;
        totalElements.value = result.totalElements;
      } catch (ex) {
        error.value = ex.message;
      } finally {
        loading.value = false;
      }
    }

    function changePage(page) {
      if (page >= 0 && page < totalPages.value) load(page);
    }

    async function importFromFolder() {
      importMessage.value = '';
      error.value = '';
      try {
        const response = await fetch('/api/import/folder', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ folder: importFolder.value })
        });
        if (!response.ok) throw new Error(await response.text());
        const result = await response.json();
        importMessage.value = `${result.message} (${result.duplicates} duplicate(s), ${result.skipped} skipped)`;
        await load();
      } catch (ex) {
        error.value = ex.message;
      }
    }

    async function uploadFiles(event) {
      const files = [...event.target.files];
      if (!files.length) return;
      const form = new FormData();
      files.forEach(file => form.append('files', file));
      try {
        const response = await fetch('/api/import/files', { method: 'POST', body: form });
        if (!response.ok) throw new Error(await response.text());
        importMessage.value = `Imported ${files.length} file(s)`;
        await load();
      } catch (ex) {
        error.value = ex.message;
      }
    }

    async function openAsset(asset) {
      selected.value = asset;
    }

    function formatDate(value) {
      if (!value) return 'Unknown date';
      return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
    }

    function size(value) {
      if (!value) return '';
      if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`;
      return `${(value / 1024 / 1024).toFixed(1)} MB`;
    }

    onMounted(async () => {
      await load();
      const response = await fetch('/api/storage');
      if (response.ok) storagePath.value = (await response.json()).path;
    });

    return { assets, currentPage, totalPages, totalElements, loading, importFolder, importMessage, error,
      storagePath, filters, selected, load, reload, changePage, importFromFolder, uploadFiles, openAsset, formatDate, size };
  },
  template
});

app.mount('#app');
