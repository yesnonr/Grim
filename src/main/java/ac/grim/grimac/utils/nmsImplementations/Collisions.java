package ac.grim.grimac.utils.nmsImplementations;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.blockdata.WrappedBlockData;
import ac.grim.grimac.utils.blockdata.types.WrappedBlockDataValue;
import ac.grim.grimac.utils.blockdata.types.WrappedDirectional;
import ac.grim.grimac.utils.blockdata.types.WrappedTrapdoor;
import ac.grim.grimac.utils.blockstate.BaseBlockState;
import ac.grim.grimac.utils.blockstate.FlatBlockState;
import ac.grim.grimac.utils.collisions.CollisionData;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.VectorData;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.enums.EntityType;
import io.github.retrooper.packetevents.utils.player.ClientVersion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Collisions {
    private static final Material HONEY_BLOCK = XMaterial.HONEY_BLOCK.parseMaterial();
    private static final Material COBWEB = XMaterial.COBWEB.parseMaterial();
    private static final Material BUBBLE_COLUMN = XMaterial.BUBBLE_COLUMN.parseMaterial();
    private static final Material SWEET_BERRY_BUSH = XMaterial.SWEET_BERRY_BUSH.parseMaterial();
    private static final Material SLIME_BLOCK = XMaterial.SLIME_BLOCK.parseMaterial();
    private static final Material POWDER_SNOW = XMaterial.POWDER_SNOW.parseMaterial();

    private static final Material LADDER = XMaterial.LADDER.parseMaterial();

    public static Vector collide(GrimPlayer player, double desiredX, double desiredY, double desiredZ) {
        if (desiredX == 0 && desiredY == 0 && desiredZ == 0) return new Vector();

        List<SimpleCollisionBox> desiredMovementCollisionBoxes = getCollisionBoxes(player, player.boundingBox.copy().expandToCoordinate(desiredX, desiredY, desiredZ));

        Vector baseCollision = collideBoundingBoxLegacy(player, new Vector(desiredX, desiredY, desiredZ), player.boundingBox, desiredMovementCollisionBoxes);

        // While running up stairs and holding space, the player activates the "lastOnGround" part without otherwise being able to step
        boolean movingIntoGround = player.lastOnGround || baseCollision.getY() != desiredY && desiredY < 0.0D;
        double stepUpHeight = player.getMaxUpStep();

        // If the player has x or z collision, is going in the downwards direction in the last or this tick, and can step up
        // If not, just return the collisions without stepping up that we calculated earlier
        if (stepUpHeight > 0.0F && movingIntoGround && (baseCollision.getX() != desiredX || baseCollision.getZ() != desiredZ)) {
            player.uncertaintyHandler.isStepMovement = true;

            // Get a list of bounding boxes from the player's current bounding box to the wanted coordinates
            List<SimpleCollisionBox> stepUpCollisionBoxes = getCollisionBoxes(player,
                    player.boundingBox.copy().expandToCoordinate(desiredX, stepUpHeight, desiredZ));


            Vector regularStepUp = collideBoundingBoxLegacy(player, new Vector(desiredX, stepUpHeight, desiredZ), player.boundingBox, stepUpCollisionBoxes);

            // 1.7 clients do not have this stepping bug fix
            if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_8)) {
                Vector stepUpBugFix = collideBoundingBoxLegacy(player, new Vector(0, stepUpHeight, 0), player.boundingBox.copy().expandToCoordinate(desiredX, 0, desiredZ), stepUpCollisionBoxes);
                if (stepUpBugFix.getY() < stepUpHeight) {
                    Vector stepUpBugFixResult = collideBoundingBoxLegacy(player, new Vector(desiredX, 0, desiredZ), player.boundingBox.copy().offset(0, stepUpBugFix.getY(), 0), stepUpCollisionBoxes).add(stepUpBugFix);
                    if (getHorizontalDistanceSqr(stepUpBugFixResult) > getHorizontalDistanceSqr(regularStepUp)) {
                        regularStepUp = stepUpBugFixResult;
                    }
                }
            }

            if (getHorizontalDistanceSqr(regularStepUp) > getHorizontalDistanceSqr(baseCollision)) {
                return regularStepUp.add(collideBoundingBoxLegacy(player, new Vector(0, -regularStepUp.getY() + desiredY, 0), player.boundingBox.copy().offset(regularStepUp.getX(), regularStepUp.getY(), regularStepUp.getZ()), stepUpCollisionBoxes));
            }
        }

        return baseCollision;
    }

    public static List<SimpleCollisionBox> getCollisionBoxes(GrimPlayer player, SimpleCollisionBox wantedBB) {
        List<SimpleCollisionBox> listOfBlocks = new ArrayList<>();
        SimpleCollisionBox expandedBB = wantedBB.copy()
                .expandMin(-0.26, -0.51, -0.26)
                .expandMax(0.26, 0.26, 0.26);

        // Worldborders were added in 1.8
        if (XMaterial.supports(8) && player.playerWorld != null) {
            WorldBorder border = player.playerWorld.getWorldBorder();
            double centerX = border.getCenter().getX();
            double centerZ = border.getCenter().getZ();
            // For some reason, the game limits the border to 29999984 blocks wide
            double size = Math.min(border.getSize() / 2, 29999984);

            // If the player's is within 16 blocks of the worldborder, add the worldborder to the collisions
            if (Math.abs(player.x + centerX) + 16 > size || Math.abs(player.z + centerZ) + 16 > size) {
                // If the player is fully within the worldborder
                if (player.boundingBox.minX > centerX - size - 1.0E-7D && player.boundingBox.maxX < centerX + size + 1.0E-7D
                        && player.boundingBox.minZ > centerZ - size - 1.0E-7D && player.boundingBox.maxZ < centerZ + size + 1.0E-7D) {
                    // South border
                    listOfBlocks.add(new SimpleCollisionBox(centerX - size, -1e33, centerZ + size, centerX + size, 1e33, centerZ + size, false));
                    // North border
                    listOfBlocks.add(new SimpleCollisionBox(centerX - size, -1e33, centerZ - size, centerX + size, 1e33, centerZ - size, false));
                    // East border
                    listOfBlocks.add(new SimpleCollisionBox(centerX + size, -1e33, centerZ - size, centerX + size, 1e33, centerZ + size, false));
                    // West border
                    listOfBlocks.add(new SimpleCollisionBox(centerX - size, -1e33, centerZ - size, centerX - size, 1e33, centerZ + size, false));
                }
            }
        }

        // Blocks are stored in YZX order
        for (int y = (int) Math.floor(expandedBB.minY); y < Math.ceil(expandedBB.maxY); y++) {
            for (int z = (int) Math.floor(expandedBB.minZ) - 1; z < Math.ceil(expandedBB.maxZ); z++) {
                for (int x = (int) Math.floor(expandedBB.minX); x < Math.ceil(expandedBB.maxX); x++) {
                    BaseBlockState data = player.compensatedWorld.getWrappedBlockStateAt(x, y, z);
                    CollisionData.getData(data.getMaterial()).getMovementCollisionBox(player, player.getClientVersion(), data, x, y, z).downCast(listOfBlocks);
                }
            }
        }

        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if (entity.type == EntityType.BOAT) {
                SimpleCollisionBox box = GetBoundingBox.getBoatBoundingBox(entity.position.getX(), entity.position.getY(), entity.position.getZ());
                if (box.isIntersected(expandedBB)) {
                    listOfBlocks.add(box);
                }
            }

            if (entity.type == EntityType.SHULKER) {
                SimpleCollisionBox box = GetBoundingBox.getBoundingBoxFromPosAndSize(entity.position.getX(), entity.position.getY(), entity.position.getZ(), 1, 1);
                if (box.isIntersected(expandedBB)) {
                    listOfBlocks.add(box);
                }
            }
        }

        return listOfBlocks;
    }

    private static Vector collideBoundingBoxLegacy(GrimPlayer player, Vector toCollide, SimpleCollisionBox box, List<SimpleCollisionBox> desiredMovementCollisionBoxes) {
        double x = toCollide.getX();
        double y = toCollide.getY();
        double z = toCollide.getZ();

        SimpleCollisionBox setBB = box.copy();

        // First, collisions are ran without any step height, in y -> x -> z order
        // In 1.14+ clients collision order is Y -> Z -> X, or if Z < X, Y -> X -> Z
        if (y != 0.0D) {
            for (SimpleCollisionBox bb : desiredMovementCollisionBoxes) {
                y = bb.collideY(setBB, y);
            }
            setBB.offset(0.0D, y, 0.0D);
        }

        boolean doZFirst = Math.abs(x) < Math.abs(z) && player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_14);
        if (doZFirst && z != 0.0D) {
            for (SimpleCollisionBox bb : desiredMovementCollisionBoxes) {
                z = bb.collideZ(setBB, z);
            }
            setBB.offset(0.0D, 0.0D, z);
        }

        if (x != 0.0D) {
            for (SimpleCollisionBox bb : desiredMovementCollisionBoxes) {
                x = bb.collideX(setBB, x);
            }
            setBB.offset(x, 0.0D, 0.0D);
        }

        if (!doZFirst && z != 0.0D) {
            for (SimpleCollisionBox bb : desiredMovementCollisionBoxes) {
                z = bb.collideZ(setBB, z);
            }
            setBB.offset(0.0D, 0.0D, z);
        }

        return new Vector(x, y, z);
    }

    private static double getHorizontalDistanceSqr(Vector vector) {
        return vector.getX() * vector.getX() + vector.getZ() * vector.getZ();
    }

    public static Vector maybeBackOffFromEdge(Vector vec3, GrimPlayer player) {
        if (!player.specialFlying && player.isSneaking && isAboveGround(player)) {
            double d = vec3.getX();
            double d2 = vec3.getZ();
            while (d != 0.0 && isEmpty(player, player.boundingBox.copy().offset(d, -player.getMaxUpStep(), 0.0))) {
                if (d < 0.05 && d >= -0.05) {
                    d = 0.0;
                    continue;
                }
                if (d > 0.0) {
                    d -= 0.05;
                    continue;
                }
                d += 0.05;
            }
            while (d2 != 0.0 && isEmpty(player, player.boundingBox.copy().offset(0.0, -player.getMaxUpStep(), d2))) {
                if (d2 < 0.05 && d2 >= -0.05) {
                    d2 = 0.0;
                    continue;
                }
                if (d2 > 0.0) {
                    d2 -= 0.05;
                    continue;
                }
                d2 += 0.05;
            }
            while (d != 0.0 && d2 != 0.0 && isEmpty(player, player.boundingBox.copy().offset(d, -player.getMaxUpStep(), d2))) {
                d = d < 0.05 && d >= -0.05 ? 0.0 : (d > 0.0 ? d - 0.05 : d + 0.05);
                if (d2 < 0.05 && d2 >= -0.05) {
                    d2 = 0.0;
                    continue;
                }
                if (d2 > 0.0) {
                    d2 -= 0.05;
                    continue;
                }
                d2 += 0.05;
            }

            vec3 = new Vector(d, vec3.getY(), d2);
        }
        return vec3;
    }

    private static boolean isAboveGround(GrimPlayer player) {
        //Player bukkitPlayer = player.bukkitPlayer;

        return player.lastOnGround || player.fallDistance < player.getMaxUpStep() &&
                !isEmpty(player, player.boundingBox.copy().offset(0.0, player.fallDistance - player.getMaxUpStep(), 0.0));
    }

    public static void handleInsideBlocks(GrimPlayer player) {
        // Use the bounding box for after the player's movement is applied
        SimpleCollisionBox aABB = GetBoundingBox.getCollisionBoxForPlayer(player, player.x, player.y, player.z).expand(-0.001);

        Location blockPos = new Location(player.playerWorld, aABB.minX, aABB.minY, aABB.minZ);
        Location blockPos2 = new Location(player.playerWorld, aABB.maxX, aABB.maxY, aABB.maxZ);

        if (CheckIfChunksLoaded.isChunksUnloadedAt(player, blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ(), blockPos2.getBlockX(), blockPos2.getBlockY(), blockPos2.getBlockZ()))
            return;

        for (int i = blockPos.getBlockX(); i <= blockPos2.getBlockX(); ++i) {
            for (int j = blockPos.getBlockY(); j <= blockPos2.getBlockY(); ++j) {
                for (int k = blockPos.getBlockZ(); k <= blockPos2.getBlockZ(); ++k) {
                    BaseBlockState block = player.compensatedWorld.getWrappedBlockStateAt(i, j, k);
                    Material blockType = block.getMaterial();

                    if (blockType == COBWEB) {
                        player.stuckSpeedMultiplier = new Vector(0.25, 0.05000000074505806, 0.25);
                    }

                    if (blockType == SWEET_BERRY_BUSH
                            && player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_14)) {
                        player.stuckSpeedMultiplier = new Vector(0.800000011920929, 0.75, 0.800000011920929);
                    }

                    if (blockType == POWDER_SNOW && i == Math.floor(player.x) && j == Math.floor(player.y) && k == Math.floor(player.z)
                            && player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_17)) {
                        player.stuckSpeedMultiplier = new Vector(0.8999999761581421, 1.5, 0.8999999761581421);
                    }

                    if (blockType == BUBBLE_COLUMN && player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_13)) {
                        BaseBlockState blockAbove = player.compensatedWorld.getWrappedBlockStateAt(i, j + 1, k);
                        BlockData bubbleData = ((FlatBlockState) block).getBlockData();
                        BubbleColumn bubbleColumn = (BubbleColumn) bubbleData;

                        if (player.playerVehicle != null && player.playerVehicle.type == EntityType.BOAT) {
                            if (!Materials.checkFlag(blockAbove.getMaterial(), Materials.AIR)) {
                                if (bubbleColumn.isDrag()) {
                                    player.clientVelocity.setY(Math.max(-0.3D, player.clientVelocity.getY() - 0.03D));
                                } else {
                                    player.clientVelocity.setY(Math.min(0.7D, player.clientVelocity.getY() + 0.06D));
                                }
                            }
                        } else {
                            if (Materials.checkFlag(blockAbove.getMaterial(), Materials.AIR)) {
                                for (VectorData vector : player.getPossibleVelocitiesMinusKnockback()) {
                                    if (bubbleColumn.isDrag()) {
                                        vector.vector.setY(Math.max(-0.9D, vector.vector.getY() - 0.03D));
                                    } else {
                                        vector.vector.setY(Math.min(1.8D, vector.vector.getY() + 0.1D));
                                    }
                                }
                            } else {
                                for (VectorData vector : player.getPossibleVelocitiesMinusKnockback()) {
                                    if (bubbleColumn.isDrag()) {
                                        vector.vector.setY(Math.max(-0.3D, vector.vector.getY() - 0.03D));
                                    } else {
                                        vector.vector.setY(Math.min(0.7D, vector.vector.getY() + 0.06D));
                                    }
                                }
                            }
                        }

                        // Reset fall distance inside bubble column
                        player.fallDistance = 0;
                    }

                    if (blockType == HONEY_BLOCK) {
                        for (VectorData vector : player.getPossibleVelocitiesMinusKnockback()) {
                            if (isSlidingDown(vector.vector, player, i, j, j)) {
                                if (vector.vector.getY() < -0.13D) {
                                    double d0 = -0.05 / vector.vector.getY();
                                    vector.vector.setX(vector.vector.getX() * d0);
                                    vector.vector.setY(-0.05D);
                                    vector.vector.setZ(vector.vector.getZ() * d0);
                                } else {
                                    vector.vector.setY(-0.05D);
                                }
                            }
                        }

                        // If honey sliding, fall distance is 0
                        player.fallDistance = 0;
                    }
                }
            }
        }
    }

    private static boolean isSlidingDown(Vector vector, GrimPlayer player, int locationX, int locationY, int locationZ) {
        if (player.onGround) {
            return false;
        } else if (player.y > locationY + 0.9375D - 1.0E-7D) {
            return false;
        } else if (vector.getY() >= -0.08D) {
            return false;
        } else {
            double d0 = Math.abs((double) locationX + 0.5D - player.lastX);
            double d1 = Math.abs((double) locationZ + 0.5D - player.lastZ);
            // Calculate player width using bounding box, which will change while swimming or gliding
            double d2 = 0.4375D + ((player.pose.width) / 2.0F);
            return d0 + 1.0E-7D > d2 || d1 + 1.0E-7D > d2;
        }
    }

    public static boolean isEmpty(GrimPlayer player, SimpleCollisionBox playerBB) {
        for (CollisionBox collisionBox : getCollisionBoxes(player, playerBB)) {
            if (collisionBox.isCollided(playerBB)) return false;
        }

        return true;
    }

    public static boolean suffocatesAt(GrimPlayer player, SimpleCollisionBox playerBB) {
        List<SimpleCollisionBox> listOfBlocks = new ArrayList<>();

        // Blocks are stored in YZX order
        for (int y = (int) Math.floor(playerBB.minY); y <= Math.ceil(playerBB.maxY); y++) {
            for (int z = (int) Math.floor(playerBB.minZ); z <= Math.ceil(playerBB.maxZ); z++) {
                for (int x = (int) Math.floor(playerBB.minX); x <= Math.ceil(playerBB.maxX); x++) {
                    BaseBlockState data = player.compensatedWorld.getWrappedBlockStateAt(x, y, z);

                    if (!data.getMaterial().isOccluding()) continue;
                    CollisionBox box = CollisionData.getData(data.getMaterial()).getMovementCollisionBox(player, player.getClientVersion(), data, x, y, z);
                    if (!box.isFullBlock()) continue;

                    box.downCast(listOfBlocks);
                }
            }
        }


        for (CollisionBox collisionBox : listOfBlocks) {
            if (collisionBox.isCollided(playerBB)) return true;
        }

        return false;
    }

    public static boolean hasBouncyBlock(GrimPlayer player) {
        return hasSlimeBlock(player) || onMaterialType(player, Materials.BED);
    }

    // Has slime block, or honey with the ViaVersion replacement block
    // This is terrible code lmao.  I need to refactor to add a new player bounding box, or somehow play with block mappings,
    // so I can automatically map honey -> slime and other important ViaVersion replacement blocks
    public static boolean hasSlimeBlock(GrimPlayer player) {
        return player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_8)
                && (onMaterial(player, SLIME_BLOCK, -0.04) ||
                (player.getClientVersion().isOlderThanOrEquals(ClientVersion.v_1_14_4)
                        && player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_8)
                        && onMaterial(player, HONEY_BLOCK, -0.04)));
    }

    public static boolean onMaterialType(GrimPlayer player, int material) {
        SimpleCollisionBox playerBB = player.boundingBox.copy().offset(0, -0.04, 0);

        // Blocks are stored in YZX order
        for (int y = (int) Math.floor(playerBB.minY); y <= Math.ceil(playerBB.maxY); y++) {
            for (int z = (int) Math.floor(playerBB.minZ); z <= Math.ceil(playerBB.maxZ); z++) {
                for (int x = (int) Math.floor(playerBB.minX); x <= Math.ceil(playerBB.maxX); x++) {
                    if (Materials.checkFlag(player.compensatedWorld.getBukkitMaterialAt(x, y, z), material))
                        return true;
                }
            }
        }

        return false;
    }

    public static boolean onMaterial(GrimPlayer player, Material material, double offset) {
        SimpleCollisionBox playerBB = player.boundingBox.copy().offset(0, -1, 0);

        // Blocks are stored in YZX order
        for (int y = (int) Math.floor(playerBB.minY); y <= Math.ceil(playerBB.maxY); y++) {
            for (int z = (int) Math.floor(playerBB.minZ); z <= Math.ceil(playerBB.maxZ); z++) {
                for (int x = (int) Math.floor(playerBB.minX); x <= Math.ceil(playerBB.maxX); x++) {
                    if (player.compensatedWorld.getBukkitMaterialAt(x, y, z) == material) return true;
                }
            }
        }

        return false;
    }

    public static boolean onClimbable(GrimPlayer player) {
        BaseBlockState blockState = player.compensatedWorld.getWrappedBlockStateAt(player.x, player.y, player.z);
        Material blockMaterial = blockState.getMaterial();

        if (Materials.checkFlag(blockMaterial, Materials.CLIMBABLE)) {
            return true;
        }

        // ViaVersion replacement block -> sweet berry bush to vines
        if (blockMaterial == SWEET_BERRY_BUSH && player.getClientVersion().isOlderThan(ClientVersion.v_1_14)) {
            return true;
        }

        return trapdoorUsableAsLadder(player, player.x, player.y, player.z, blockState);
    }

    private static boolean trapdoorUsableAsLadder(GrimPlayer player, double x, double y, double z, BaseBlockState blockData) {
        if (!Materials.checkFlag(blockData.getMaterial(), Materials.TRAPDOOR)) return false;

        WrappedBlockDataValue blockDataValue = WrappedBlockData.getMaterialData(blockData);
        WrappedTrapdoor trapdoor = (WrappedTrapdoor) blockDataValue;

        if (trapdoor.isOpen()) {
            BaseBlockState blockBelow = player.compensatedWorld.getWrappedBlockStateAt(x, y - 1, z);

            if (blockBelow.getMaterial() == LADDER) {
                WrappedBlockDataValue belowData = WrappedBlockData.getMaterialData(blockBelow);

                WrappedDirectional ladder = (WrappedDirectional) belowData;
                return ladder.getDirection() == trapdoor.getDirection();
            }
        }

        return false;
    }
}
