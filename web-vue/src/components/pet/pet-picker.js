import * as THREE from "three";

const CAT_EDGE_MARGIN = 0.17;

// 返回画布指针下的可见物件，猫的脸和身体是仅有的抚摸区域
export function createPetPicker(camera, catParts, spots) {
  const ray = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const dragPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0));
  const furniture = new Map();
  const targets = catParts.slice();
  for (const spot of spots) {
    furniture.set(spot.object, spot.name);
    targets.push(spot.object);
  }

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

  // 按渲染画布的位置取射线，区分前景物件、猫和地面
  function read(event, canvas) {
    aim(event, canvas);
    const hits = ray.intersectObjects(targets, true);
    let cat = null;
    let ground = null;
    for (const hit of hits) {
      const name = readTarget(hit.object);
      if (name === "pet" && !cat) {
        cat = hit;
      } else if (name === "floor" || name === "rug") {
        if (!ground) {
          ground = { name: name, point: hit.point };
        }
      } else if (name && name !== "pet") {
        // 在猫身体边缘附近时，把点按留给紧挨着的摆件
        if (!cat || hit.distance - cat.distance <= CAT_EDGE_MARGIN) {
          return { name: name, point: hit.point };
        }
        break;
      }
    }
    if (cat) {
      return { name: "pet", point: cat.point };
    }
    if (ground) {
      return ground;
    }
    return { name: "floor", point: null };
  }

  // 射线也能穿过隐藏网格，所以要检查模型和它的父节点是否可见
  function readTarget(object) {
    let parent = object;
    let name = "";
    while (parent) {
      if (!parent.visible) {
        return "";
      }
      if (catParts.includes(parent)) {
        name = "pet";
      } else if (furniture.has(parent)) {
        name = furniture.get(parent);
      }
      parent = parent.parent;
    }
    return name;
  }

  return { read: read, readDragPoint: readDragPoint };
}
