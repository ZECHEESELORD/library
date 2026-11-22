/**
 * The client-first camera rig stack (architecture section 7.3): seven stages evaluated top to bottom every rendered
 * frame, producing one {@link sh.harold.library.camera.rig.CameraState}. This package is host-free; the Fabric client
 * adapter drives the vanilla camera from the state. It shares nothing with the server-side {@code camera-motion}
 * feature.
 */
package sh.harold.library.camera.rig;
