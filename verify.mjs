import fs from 'node:fs';import assert from 'node:assert/strict';
const base=new URL('./',import.meta.url),asset=new URL('app/src/main/assets/',base);
const read=n=>JSON.parse(fs.readFileSync(new URL(n,asset)));
const weapons=read('weapons.json').data,index=read('image-index.json'),info=read('skin-info.json'),tiers=read('tiers.json');
const categories=new Set(),ids=new Set(),required=new Set();let count=0,variants=0;
for(const w of weapons){assert(w.displayName&&Array.isArray(w.skins));categories.add(w.category.replace('EEquippableCategory::',''));if(w.displayIcon)required.add(w.displayIcon);
  for(const s of w.skins){const id=s.levels?.[0]?.uuid;assert(s.displayName&&id);assert(!ids.has(id));ids.add(id);count++;
    const primary=s.displayIcon||s.levels[0].displayIcon;if(primary)required.add(primary);
    for(const ch of s.chromas){variants++;if(ch.fullRender)required.add(ch.fullRender);if(ch.swatch)required.add(ch.swatch);}
  }
}for(const t of tiers)if(t.displayIcon)required.add(t.displayIcon);
for(const url of required){assert(index[url],`Missing packed asset: ${url}`);assert(fs.existsSync(new URL(index[url],asset)),`Missing file ${index[url]}`);}
const files=new Set(Object.values(index));let bytes=0;
for(const name of files){const data=fs.readFileSync(new URL(name,asset));assert.equal(data.subarray(0,4).toString(),'RIFF');assert.equal(data.subarray(8,12).toString(),'WEBP');bytes+=data.length;}
const reaver=weapons.find(w=>w.displayName==='Operator').skins.find(s=>s.displayName==='Reaver Operator');assert.equal(info[reaver.levels[0].uuid.replaceAll('-','')].collection,'Reaver');
const source=fs.readFileSync(new URL('app/src/main/java/app/quietshop/MainActivity.java',base),'utf8');
for(const category of categories)assert(source.includes(`"${category}"`));
assert(source.includes('"All Skins","Shop","Starred"'));assert(source.includes('state==null?1:'));assert(!source.includes('Download gallery')&&!source.includes('Update catalogue')&&!source.includes('QuietShop'));
const manifest=fs.readFileSync(new URL('app/src/main/AndroidManifest.xml',base),'utf8');assert(!/POST_NOTIFICATIONS|RECEIVE_BOOT_COMPLETED/.test(manifest));assert(manifest.includes('android:allowBackup="false"'));
const widget=fs.readFileSync(new URL('app/src/main/res/xml/shop_widget.xml',base),'utf8');assert(widget.includes('android:updatePeriodMillis="0"'));assert(widget.includes('android:targetCellWidth="5"'));assert(widget.includes('android:targetCellHeight="1"'));
console.log(`Verified ${count} skins, ${variants} variant entries, ${required.size} packed image references, ${files.size} unique WebP assets (${(bytes/1e6).toFixed(1)} MB). Category, navigation and widget checks passed.`);
