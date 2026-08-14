export const formatCurrency=v=>v===null||v===undefined||v===''?'—':new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(v))
export const formatNumber=v=>v===null||v===undefined||v===''?'—':new Intl.NumberFormat('vi-VN',{maximumFractionDigits:2}).format(Number(v))
export function formatDate(v){if(!v)return'—';const d=new Date(`${v}T00:00:00`);return Number.isNaN(d.getTime())?v:new Intl.DateTimeFormat('vi-VN').format(d)}
export function formatDateTime(v){if(!v)return'—';const d=new Date(v);return Number.isNaN(d.getTime())?v:new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short'}).format(d)}
export function toDateTimeLocal(v){if(!v)return'';const d=new Date(v);if(Number.isNaN(d.getTime()))return'';const offset=d.getTimezoneOffset();return new Date(d.getTime()-offset*60000).toISOString().slice(0,16)}
export const isLowStock=i=>Number(i?.quantity??0)<=Number(i?.lowStockThreshold??0)
