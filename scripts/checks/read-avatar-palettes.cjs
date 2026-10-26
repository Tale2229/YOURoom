// Read-only, intentionally narrow evaluator for the current Kotlin color definitions.
const fs = require('node:fs');
const path = require('node:path');
const root = require('./paths.cjs').root;
const theme = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/youroom/ui/theme/Theme.kt'), 'utf8');
const roles = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/youroom/ui/theme/ThemeRoles.kt'), 'utf8');
const entries = [
 ['SAND','砂岩',1],['PINE','松绿',1],['MATISSE','马蒂斯',1],['DUNHUANG','敦煌',1],
 ['OLD_PORT','旧港来信',9],['PEACH_PLUM','蜜桃乌梅',2],['BURGUNDY','勃艮第',4],
 ['MORANDI','莫兰迪',4],['RED_BEAN','入骨相思',4],['AUTUMN_CITRUS','橙黄橘绿时',5],
 ['MORNING_BELL','晨钟暮鼓',7],['MONDRIAN','蒙德里安',1],
];
const fields = ['primary','onPrimary','primaryContainer','background','surface','onSurface','onSurfaceVariant','outlineVariant','outline','surfaceContainerLowest','surfaceContainerLow','surfaceContainer','surfaceContainerHigh','surfaceContainerHighest'];
function args(text) {
 let depth=0,start=0,out=[];
 for(let i=0;i<text.length;i++) { if(text[i]==='(')depth++; if(text[i]===')')depth--; if(text[i]===','&&!depth){out.push(text.slice(start,i));start=i+1;} }
 return [...out,text.slice(start)].map(x=>x.trim()).filter(Boolean);
}
function evaluate(expr,c,dark) {
 expr=expr.trim();
 const branch=expr.match(/^if\s*\(dark(?:Theme)?\)\s*([\s\S]+?)\s+else\s+([\s\S]+)$/);
 if(branch)return evaluate(branch[dark?1:2],c,dark);
 const alpha=expr.match(/^(.+)\.copy\(alpha\s*=\s*([.\d]+)f\)$/);
 if(alpha)return evaluate(alpha[1],c,dark).slice(0,7)+Math.round(Number(alpha[2])*255).toString(16).padStart(2,'0').toUpperCase();
 const color=expr.match(/^Color\(0xFF([A-F\d]{6})\)$/i);
 if(color)return '#'+color[1].toUpperCase();
 if(expr==='Color.White')return '#FFFFFF';
 if(expr==='false')return false;
 if(expr==='true')return true;
 if(expr==='null')return null;
 if(expr.startsWith('c.')&&c[expr.slice(2)])return c[expr.slice(2)];
 const local={blue:'#343E47',pale:'#E9EFE4',yellow:c.tertiary};
 if(local[expr])return local[expr];
 throw new Error('Unsupported Kotlin color expression: '+expr);
}
function assignments(body,c,dark){
 const result={};
 for(const arg of args(body.replace(/\/\/[^\n]*/g,''))){const m=arg.match(/^(\w+)\s*=\s*([\s\S]+)$/);if(m)result[m[1]]=evaluate(m[2],c,dark);}
 return result;
}
function extract(id,dark){
 const palettes=theme.slice(theme.indexOf('private fun tonePalette'));
 const match=palettes.match(new RegExp('CampusTone\\.'+id+' -> if\\(dark\\) palette\\(([^)]+)\\)\\s*else palette\\(([^)]+)\\)'));
 if(!match)throw new Error('Missing palette '+id);
 const values=[...match[dark?1:2].matchAll(/0xFF([A-F\d]{6})/gi)].map(m=>'#'+m[1]);
 if(values.length!==14)throw new Error('Wrong palette size '+id);
 const c=Object.fromEntries(fields.map((f,i)=>[f,values[i]]));
 c.secondary=c.tertiary=c.primary;c.onTertiary=c.onPrimary;
 const scheme=theme.match(new RegExp('(?:return if|else if) \\(tone == CampusTone\\.'+id+'\\) colors\\.copy\\(([\\s\\S]*?)\\n    \\)'));
 if(scheme)Object.assign(c,assignments(scheme[1],c,dark));
 let r={header:c.surface,onHeader:c.onSurface,headerMuted:c.onSurfaceVariant,navigation:c.surface,navigationSelected:c.primary,onNavigationSelected:c.surface,navigationIdle:c.primary,settingsIcon:c.primary,themeArrow:c.primary,impressionFrame:evaluate('c.surfaceContainerHighest.copy(alpha=.3f)',c,dark),divider:c.outlineVariant,hideHeaderDivider:false,switchTrackOn:c.primary,switchTrackOff:c.outlineVariant,switchThumb:'#FFFFFF',periodIdle:c.outlineVariant,listDivider:c.outlineVariant};
 const block=roles.match(new RegExp('if \\(tone == CampusTone\\.'+id+'\\) return legacy\\.copy\\(([\\s\\S]*?)\\n    \\)'));
 if(block)Object.assign(r,assignments(block[1],c,dark));
 else if(id==='MATISSE')Object.assign(r,assignments(roles.slice(roles.lastIndexOf('return legacy.copy(')+'return legacy.copy('.length,roles.lastIndexOf('\n    )')),c,dark));
 return {...c,...r};
}
const data=entries.map(([id,name,version])=>({id,name,version,image:name+'/avatar-generated-v'+version+'.png',light:extract(id,false),dark:extract(id,true)}));
module.exports=data;
if(require.main===module)process.stdout.write(JSON.stringify(data));
