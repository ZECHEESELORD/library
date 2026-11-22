/**
 * Movement as pluggable abilities over vanilla physics (architecture section 5). Each tick the host reads a
 * {@link sh.harold.library.movement.BodyState} and an {@link sh.harold.library.movement.InputFrame}, the core answers
 * with a {@link sh.harold.library.movement.MoveOutput} of modifiers, and the host's own movement code then runs as
 * usual. Host-free: no Minecraft or loader types.
 */
package sh.harold.library.movement;
