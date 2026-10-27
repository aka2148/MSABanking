export interface TriggerRect { top:number; bottom:number; left:number; right:number; width:number; }
export function overlayPlacement(rect:TriggerRect, viewportWidth:number, viewportHeight:number, desiredHeight=280) {
  const gutter=8, gap=6;
  const below=Math.max(0,viewportHeight-rect.bottom-gap-gutter);
  const above=Math.max(0,rect.top-gap-gutter);
  const upward=below<desiredHeight&&above>below;
  const maxHeight=Math.min(desiredHeight,upward?above:below);
  const width=Math.min(rect.width,Math.max(0,viewportWidth-2*gutter));
  return {left:Math.max(gutter,Math.min(rect.left,viewportWidth-width-gutter)),
    top:upward?rect.top-gap-maxHeight:rect.bottom+gap,width,maxHeight,upward,
    visible:rect.bottom>0&&rect.top<viewportHeight&&rect.right>0&&rect.left<viewportWidth};
}
