import * as THREE from "three";

// 将可见场景的网格作为点选范围，按镜头距离决定指针实际碰到的物体
export function createPetPicker(camera, petRoot, spots) {
  const ray = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const dragPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0));
  const clockPlane = new THREE.Plane(new THREE.Vector3(0, 0, 1));
  const furniture = new Map();
  const targets = new Set();
  if (petRoot) {
    targets.add(petRoot);
    furniture.set(petRoot, "pet");
  }
  for (const spot of spots) {
    furniture.set(spot.object, spot.name);
    let parent = spot.object.parent;
    while (parent && !targets.has(parent)) {
      parent = parent.parent;
    }
    if (!parent) {
      targets.add(spot.object);
    }
  }
  const pickRoots = Array.from(targets);

  // 把画布上的指针位置换成相机射线，点选和拖动共用这个换算
  function aim(event, canvas) {
    const area = canvas.getBoundingClientRect();
    const horizontal = (event.clientX - area.left) / area.width * 2 - 1;
    const vertical = 1 - (event.clientY - area.top) / area.height * 2;
    pointer.set(horizontal, vertical);
    ray.setFromCamera(pointer, camera);
  }

  // 返回指针在指定高度的平面上的落点，拖动时保持物件贴着地面
  function readDragPoint(event, canvas, height) {
    aim(event, canvas);
    dragPlane.constant = -height;
    return ray.ray.intersectPlane(dragPlane, new THREE.Vector3());
  }

  // 将指针投到挂钟正面，拖动越过表盘边缘后仍可计算方向
  function readClockPoint(event, canvas, face) {
    aim(event, canvas);
    const center = face.getWorldPosition(new THREE.Vector3());
    clockPlane.normal.set(0, 0, 1);
    clockPlane.normal.transformDirection(face.matrixWorld);
    clockPlane.setFromNormalAndCoplanarPoint(clockPlane.normal, center);
    const point = ray.ray.intersectPlane(clockPlane, new THREE.Vector3());
    if (!point) {
      return null;
    }
    return face.worldToLocal(point);
  }

  // 按渲染画布的位置取射线，区分前景物件、猫和地面
  function read(event, canvas) {
    aim(event, canvas);
    const hits = ray.intersectObjects(pickRoots, true);
    for (const hit of hits) {
      const name = readTarget(hit.object);
      if (name) {
        return { name: name, point: hit.point };
      }
    }
    return { name: "", point: null };
  }

  // 射线也能穿过隐藏网格，所以要检查模型和它的父节点是否可见
  function readTarget(object) {
    // 宠物的透明额纹贴花不能扩出可摸区域，真实皮肤网格仍参与命中
    const material = object.material;
    if (material) {
      if (!material.visible || (material.transparent && !material.depthWrite)) {
        return "";
      }
    }
    let parent = object;
    let name = "";
    while (parent) {
      if (!parent.visible) {
        return "";
      }
      if (!name && furniture.has(parent)) {
        name = furniture.get(parent);
      }
      parent = parent.parent;
    }
    return name;
  }

  return { read: read, readDragPoint: readDragPoint, readClockPoint: readClockPoint };
}
