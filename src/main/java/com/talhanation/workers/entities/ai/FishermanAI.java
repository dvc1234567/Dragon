package com.talhanation.workers.entities.ai;

import com.talhanation.workers.Main;
import com.talhanation.workers.Translatable;
import com.talhanation.workers.entities.FishermanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.*;

import static com.talhanation.workers.entities.FishermanEntity.State.*;

public class FishermanAI extends Goal {
    private final FishermanEntity fisherman;
    private int fishingTimer = 100;
    private int throwTimer = 0;
    private int fishingRange;
    private BlockPos fishingPos = null;
    private BlockPos coastPos;
    private Boat boat;
    private FishermanEntity.State state;
    private List<BlockPos> waterBlocks;
    private int timer;
    private byte fails;

    public FishermanAI(FishermanEntity fishermanEntity) {
        this.fisherman = fishermanEntity;
    }

    @Override
    public boolean canUse() {
        return fisherman.isTame() && fisherman.getStartPos() != null;

    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.coastPos = this.getCoastPos();
        if(coastPos == null){
            coastPos = fisherman.getStartPos();
        }

        this.timer = 0;
        this.setWorkState(FishermanEntity.State.fromIndex(fisherman.getState()));
        super.start();
    }

    @Override
    public void tick() {
        Main.LOGGER.info("timer: " + timer);
        Main.LOGGER.info("State: " + state);
        switch (state){
            case IDLE -> {
                if(fisherman.getStartPos() != null && fisherman.canWork()){

                    this.setWorkState(MOVING_COAST);
                }
            }

            case MOVING_COAST -> {
                if(!fisherman.canWork()) this.setWorkState(STOPPING);
                if(fisherman.getVehicle() != null) fisherman.stopRiding();

                if (coastPos == null) coastPos = fisherman.getStartPos();
                else
                    this.moveToPos(coastPos);

                if (coastPos.closerThan(fisherman.getOnPos(), 3F)) {
                    List<Boat> list =  fisherman.level.getEntitiesOfClass(Boat.class, fisherman.getBoundingBox().inflate(8D));
                    list.removeIf(boat -> !boat.getPassengers().isEmpty());
                    list.sort(Comparator.comparing(boatInList -> boatInList.distanceTo(fisherman)));
                    if(!list.isEmpty()){
                        boat = list.get(0);
                        fishingRange = 20;

                        this.setWorkState(MOVING_TO_BOAT);
                    }
                    else {
                        fishingRange = 5;
                        this.setWorkState(FISHING);
                    }

                    this.findWaterBlocks();
                    if(!waterBlocks.isEmpty()) fishingPos = waterBlocks.get(fisherman.getRandom().nextInt(waterBlocks.size()));

                    if(fishingPos == null) this.setWorkState(STOPPING);

                }

            }

            case MOVING_TO_BOAT -> {
                if(boat != null && boat.getPassengers().isEmpty()){
                    if(!fisherman.canWork()) {
                        this.setWorkState(STOPPING);
                    }

                    this.moveToPos(boat.getOnPos());

                    if (coastPos.closerThan(fisherman.getOnPos(), 10F)) {
                        fisherman.startRiding(boat);
                    }
                    else if(++timer > 200){
                        this.setWorkState(STOPPING);
                        timer = 0;
                    }

                    if(boat.getFirstPassenger() != null && this.fisherman.equals(boat.getFirstPassenger())){
                        this.fisherman.setSailPos(fishingPos);
                        this.setWorkState(SAILING);
                    }
                }
                else{
                    this.setWorkState(IDLE);
                }

            }

            case SAILING -> {
                if(!fisherman.canWork()) this.setWorkState(STOPPING);
                if(fishingPos == null){
                    this.setWorkState(IDLE);
                    break;
                }
                double distance = fisherman.distanceToSqr(fishingPos.getX(), fishingPos.getY(), fishingPos.getZ());
                if(distance < 8.5F) { //valid value example: distance = 3.2
                    this.setWorkState(FISHING);
                }
                else if(++timer > 200){
                    timer = 0;
                    fails++;

                    if(fails == 3){
                        this.setWorkState(STOPPING);
                        timer = 0;
                    }
                    else {
                        this.findWaterBlocks();
                        if(!waterBlocks.isEmpty()) fishingPos = waterBlocks.get(fisherman.getRandom().nextInt(waterBlocks.size()));
                        if(fishingPos == null){
                            this.setWorkState(STOPPING);
                            break;
                        }

                        this.fisherman.setSailPos(fishingPos);
                        this.setWorkState(SAILING);

                    }
                }
            }

            case FISHING -> {
                if(!fisherman.canWork()) this.setWorkState(STOPPING);

                if(fishingPos == null) this.setWorkState(STOPPING);
                fishing();
            }

            case STOPPING -> {
                if(coastPos != null) {
                    if (boat != null && boat.getFirstPassenger() != null && this.fisherman.equals(boat.getFirstPassenger())) {
                        this.fisherman.setSailPos(coastPos);
                    } else{
                        this.moveToPos(coastPos);
                        fisherman.stopRiding();
                    }

                    double distance = fisherman.distanceToSqr(coastPos.getX(), coastPos.getY(), coastPos.getZ());
                    if(distance < 6.0F) { //valid value example: distance = 3.2
                        fisherman.stopRiding();
                        this.setWorkState(STOP);
                    }
                    else if(++timer > 200){
                        fisherman.stopRiding();
                        this.setWorkState(STOP);
                        timer = 0;
                    }
                }
                else
                    this.setWorkState(IDLE);
            }

            case STOP -> {
                fisherman.stopRiding();
                if(fisherman.needsToDeposit()){
                    setWorkState(DEPOSIT);
                }
                else{
                    this.fisherman.walkTowards(coastPos, 1);

                    double distance = fisherman.distanceToSqr(coastPos.getX(), coastPos.getY(), coastPos.getZ());
                    if(distance < 5.5F) { //valid value example: distance = 3.2
                        stop();
                    }
                }
            }

            case DEPOSIT -> {
                //Seperate AI doing stuff
                fisherman.stopRiding();
                if(!fisherman.needsToDeposit()){
                    setWorkState(STOP);
                }
            }
        }
    }

    private void setWorkState(FishermanEntity.State state) {
        timer = 0;
        this.state = state;
        this.fisherman.setState(state.getIndex());
    }

    private void moveToPos(BlockPos pos) {
        if(pos != null) {
            //Move to Pos -> normal movement
            if (!pos.closerThan(fisherman.getOnPos(), 12F)) {
                this.fisherman.walkTowards(pos, 1F);
            }
            //Near Pos -> presice movement
            if (!pos.closerThan(fisherman.getOnPos(), 2F)) {
                this.fisherman.getMoveControl().setWantedPosition(pos.getX(), fisherman.getStartPos().getY(), pos.getZ(), 1);
            }
        }
    }

    @Override
    public void stop() {
        this.fishingPos = null;
        this.fishingTimer = 0;
        this.resetTask();
        this.setWorkState(IDLE);
        super.stop();
    }

    public void resetTask() {
        fisherman.getNavigation().stop();
        this.fishingTimer = fisherman.getRandom().nextInt(600);
    }



    private void findWaterBlocks() {
        this.waterBlocks = new ArrayList<>();

        for (int x = -this.fishingRange; x < this.fishingRange; ++x) {
            for (int y = -2; y < 2; ++y) {
                for (int z = -this.fishingRange; z < this.fishingRange; ++z) {
                    if (coastPos != null) {
                        BlockPos pos = this.coastPos.offset(x, y, z);
                        BlockState targetBlock = this.fisherman.level.getBlockState(pos);

                        if (targetBlock.is(Blocks.WATER) && fisherman.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > fishingRange) {
                            for( int i = 0; i < 4 ;i++){
                                if(this.fisherman.level.getBlockState(pos.above(i)).isAir() && i == 3){
                                    if(hasWaterConnection(coastPos, pos)) this.waterBlocks.add(pos);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        this.waterBlocks.sort(Comparator.comparing(this::getWaterDepth));
        if (waterBlocks.isEmpty()) {
            // No water nearby
            if (fisherman.getOwner() != null) {
                fisherman.tellPlayer(fisherman.getOwner(), Translatable.TEXT_FISHER_NO_WATER);
                this.fisherman.setIsWorking(false, true);
                this.fisherman.clearStartPos();
                this.stop();
            }
        }
    }

    private boolean hasWaterConnection(BlockPos start, BlockPos end) {
        // Calculate the horizontal distance between start and end
        int dx = Math.abs(start.getX() - end.getX());
        int dz = Math.abs(start.getZ() - end.getZ());

        // Check if the horizontal distance is within your desired range
        int maxHorizontalDistance = fishingRange; // Adjust this range as needed
        if (dx <= maxHorizontalDistance && dz <= maxHorizontalDistance) {
            // Now, check for vertical clearance (air blocks)
            int startY = Math.min(start.getY(), end.getY());
            int endY = Math.max(start.getY(), end.getY());

            for (int y = startY + 1; y < endY; y++) {
                BlockPos checkPos = new BlockPos(start.getX(), y, start.getZ());
                BlockState checkState = this.fisherman.level.getBlockState(checkPos);

                if (!checkState.is(Blocks.WATER)) {
                    return false; // There's an obstacle in the way
                }
            }

            // If no obstacles were found, there is a water connection
            return true;
        }

        return false; // Horizontal distance too far for a connection
    }


    public void spawnFishingLoot() {
        int depth;
        if (fishingPos != null) {
            depth = 1 + ((this.getWaterDepth(fishingPos) + fishingRange) / 10);
        }
        else
            depth = 1;

        this.fishingTimer = 500 + fisherman.getRandom().nextInt(1000) / depth;
        double luck = 0.1D;
        LootContext.Builder lootcontext$builder = (new LootContext.Builder((ServerLevel)fisherman.level))
                .withParameter(LootContextParams.ORIGIN, fisherman.position())
                .withParameter(LootContextParams.TOOL, this.fisherman.getItemInHand(InteractionHand.MAIN_HAND))
                .withLuck((float) luck);


        MinecraftServer server = fisherman.getServer();
        if (server == null) return;
        LootTable loottable = server.getLootTables().get(BuiltInLootTables.FISHING);
        List<ItemStack> list = loottable.getRandomItems(lootcontext$builder.create(LootContextParamSets.FISHING));

        for (ItemStack itemstack : list) {
            fisherman.getInventory().addItem(itemstack);
        }
    }

    private void fishing(){
        if (this.fisherman.getVehicle() == null && !coastPos.closerThan(fisherman.getOnPos(), 5F)) {
            this.moveToPos(coastPos);
        }
        // Look at the water block
        if (fishingPos != null) {
            this.fisherman.getLookControl().setLookAt(
                    fishingPos.getX(),
                    fishingPos.getY() + 1,
                    fishingPos.getZ(),
                    10.0F,
                    (float) this.fisherman.getMaxHeadXRot()
            );


            if (throwTimer == 0) {
                fisherman.playSound(SoundEvents.FISHING_BOBBER_THROW, 1, 0.5F);
                this.fisherman.swing(InteractionHand.MAIN_HAND);
                throwTimer = fisherman.getRandom().nextInt(400);
                // TODO: Create FishingBobberEntity compatible with AbstractEntityWorker.
                // WorkersFishingHook fishingHook = new WorkersFishingHook(this.fisherman, fisherman.level, fishingPos);
                // fisherman.level.addFreshEntity(fishingHook);
            }

            if (fishingTimer > 0) fishingTimer--;

            if (fishingTimer == 0) {
                // Get the loot
                this.spawnFishingLoot();
                this.fisherman.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 1, 1);
                this.fisherman.swing(InteractionHand.MAIN_HAND);
                this.fisherman.increaseFarmedItems();
                this.fisherman.consumeToolDurability();
                this.resetTask();

            }
        }
        if (throwTimer > 0) throwTimer--;
    }


    private int getWaterDepth(BlockPos pos){
        int depth = 0;
        for(int i = 0; i < 10; i++){
            BlockState state = fisherman.level.getBlockState(pos.below(i));
            if(state.is(Blocks.WATER)){
                depth++;
            }
            else break;
        }
        return depth;
    }

    private BlockPos getCoastPos() {
        List<BlockPos> list = new ArrayList<>();
        for(int i = 0; i < 10; i++){
            for(int k = 0; k < 10; k++) {
                BlockPos pos = fisherman.getStartPos().offset(i, 0, k);
                BlockState targetBlockN = this.fisherman.level.getBlockState(pos.north());
                BlockState targetBlockE = this.fisherman.level.getBlockState(pos.east());
                BlockState targetBlockS = this.fisherman.level.getBlockState(pos.south());
                BlockState targetBlockW = this.fisherman.level.getBlockState(pos.west());

                if (targetBlockN.is(Blocks.WATER) || targetBlockE.is(Blocks.WATER) || targetBlockS.is(Blocks.WATER) || targetBlockW.is(Blocks.WATER) ) {
                    list.add(pos);
                }
            }
        }


        if(list.isEmpty()) {
            return fisherman.getStartPos();
        }
        else {
            list.sort(Comparator.comparing(blockPos -> blockPos.distSqr(fisherman.getStartPos())));
            return list.get(0);
        }
    }
}
