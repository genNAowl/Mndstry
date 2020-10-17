package mindustry.world;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.liquid.*;

import static mindustry.Vars.*;

public class Build{
    private static final IntSet tmp = new IntSet();

    @Remote(called = Loc.server)
    public static void beginBreak(Team team, int x, int y){
        if(!validBreak(team, x, y)){
            return;
        }

        Tile tile = world.tileBuilding(x, y);
        //this should never happen, but it doesn't hurt to check for links
        float prevPercent = 1f;

        if(tile.build != null){
            prevPercent = tile.build.healthf();
        }

        int rotation = tile.build != null ? tile.build.rotation : 0;
        Block previous = tile.block();
        Block sub = ConstructBlock.get(previous.size);

        tile.setBlock(sub, team, rotation);
        tile.<ConstructBuild>bc().setDeconstruct(previous);
        tile.build.health = tile.build.maxHealth * prevPercent;


        Core.app.post(() -> Events.fire(new BlockBuildBeginEvent(tile, team, true)));
    }

    /** Places a BuildBlock at this location. */
    @Remote(called = Loc.server)
    public static void beginPlace(Block result, Team team, int x, int y, int rotation){
        if(!validPlace(result, team, x, y, rotation)){
            return;
        }

        Tile tile = world.tile(x, y);

        //just in case
        if(tile == null) return;

        Block previous = tile.block();
        Block sub = ConstructBlock.get(result.size);
        Seq<Building> prevBuild = new Seq<>(9);

        result.beforePlaceBegan(tile, previous);
        tmp.clear();

        tile.getLinkedTilesAs(result, t -> {
            if(t.build != null && t.build.team == team && tmp.add(t.build.id)){
                prevBuild.add(t.build);
            }
        });

        tile.setBlock(sub, team, rotation);

        ConstructBuild build = tile.bc();

        build.setConstruct(previous.size == sub.size ? previous : Blocks.air, result);
        build.prevBuild = prevBuild;

        result.placeBegan(tile, previous);

        Core.app.post(() -> Events.fire(new BlockBuildBeginEvent(tile, team, false)));
    }

    /** Returns whether a tile can be placed at this location by this team. */
    public static boolean validPlace(Block type, Team team, int x, int y, int rotation){
        return validPlace(type, team, x, y, rotation, true);
    }

    /** Returns whether a tile can be placed at this location by this team. */
    public static boolean validPlace(Block type, Team team, int x, int y, int rotation, boolean checkVisible){
        //the wave team can build whatever they want as long as it's visible - banned blocks are not applicable
        if(type == null || (checkVisible && (!type.isPlaceable() && !(state.rules.waves && team == state.rules.waveTeam && type.isVisible())))){
            return false;
        }

        if((type.solid || type.solidifes) && Units.anyEntities(x * tilesize + type.offset - type.size*tilesize/2f, y * tilesize + type.offset - type.size*tilesize/2f, type.size * tilesize, type.size*tilesize)){
            return false;
        }

        if(state.teams.eachEnemyCore(team, core -> Mathf.dst(x * tilesize + type.offset, y * tilesize + type.offset, core.x, core.y) < state.rules.enemyCoreBuildRadius + type.size * tilesize / 2f)){
            return false;
        }

        Tile tile = world.tile(x, y);

        if(tile == null) return false;

        //campaign darkness check
        if(world.getDarkness(x, y) >= 3){
            return false;
        }

        if(!type.requiresWater && !contactsShallows(tile.x, tile.y, type) && !contactsBuoys(tile.x, tile.y, type) && !type.placeableLiquid){
            return false;
        }

        if(!type.canPlaceOn(tile, team)){
            return false;
        }

        int offsetx = -(type.size - 1) / 2;
        int offsety = -(type.size - 1) / 2;

        for(int dx = 0; dx < type.size; dx++){
            for(int dy = 0; dy < type.size; dy++){
                int wx = dx + offsetx + tile.x, wy = dy + offsety + tile.y;


                Tile check = world.tile(wx, wy);

                if(
                check == null || //nothing there
                (check.floor().isDeep() && !type.floating && !type.requiresWater && !type.placeableLiquid && !contactsBuoys(tile.x, tile.y, type)) ||  //deep water
                (type == check.block() && check.build != null && rotation == check.build.rotation && type.rotate) || //same block, same rotation
                !check.interactable(team) || //cannot interact
                !check.floor().placeableOn || //solid wall
                    !((type.canReplace(check.block()) || //can replace type
                        (check.block instanceof ConstructBlock && check.<ConstructBuild>bc().cblock == type && check.centerX() == tile.x && check.centerY() == tile.y)) && //same type in construction
                    type.bounds(tile.x, tile.y, Tmp.r1).grow(0.01f).contains(check.block.bounds(check.centerX(), check.centerY(), Tmp.r2))) || //no replacement
                (type.requiresWater && check.floor().liquidDrop != Liquids.water) //requires water but none found
                ) return false;
            }
        }

        return true;
    }

    public static boolean contactsGround(int x, int y, Block block){
        if(block.isMultiblock()){
            for(Point2 point : Edges.getEdges(block.size)){
                Tile tile = world.tile(x + point.x, y + point.y);
                if(tile != null && !tile.floor().isLiquid) return true;
            }
        }else{
            for(Point2 point : Geometry.d4){
                Tile tile = world.tile(x + point.x, y + point.y);
                if(tile != null && !tile.floor().isLiquid) return true;
            }
        }
        return false;
    }

    public static boolean contactsShallows(int x, int y, Block block){
        if(block.isMultiblock()){
            for(Point2 point : Edges.getInsideEdges(block.size)){
                Tile tile = world.tile(x + point.x, y + point.y);
                if(tile != null && !tile.floor().isDeep()) return true;
            }

            for(Point2 point : Edges.getEdges(block.size)){
                Tile tile = world.tile(x + point.x, y + point.y);
                if(tile != null && !tile.floor().isDeep()) return true;
            }
        }else{
            for(Point2 point : Geometry.d4){
                Tile tile = world.tile(x + point.x, y + point.y);
                if(tile != null && !tile.floor().isDeep()) return true;
            }
            Tile tile = world.tile(x, y);
            return tile != null && !tile.floor().isDeep();
        }
        return false;
    }

    public static boolean contactsBuoys(int x, int y, Block block) {
        if(block.isMultiblock()){
            for(Point2 point : Edges.getInsideEdges(block.size)){
                Tile tile = world.tile(x + point.x, y + point.y);
                if(tile != null && tile.block() instanceof Buoy) return false;
            }

            for(Point2 point : Edges.getEdges(block.size)){
                Tile tile = world.tile(x + point.x, y + point.y);
                if(tile != null && tile.block() instanceof Buoy) return true;
            }
        }else{
            Tile tile = world.tile(x, y);
            if(tile != null && tile.block() instanceof Buoy) return false;

            for(Point2 point : Geometry.d4){
                tile = world.tile(x + point.x, y + point.y);
                if(tile != null && tile.block() instanceof Buoy) return true;
            }
        }
        return false;
    }

    /** Returns whether the tile at this position is breakable by this team */
    public static boolean validBreak(Team team, int x, int y){
        Tile tile = world.tile(x, y);
        return tile != null && tile.block().canBreak(tile) && tile.breakable() && tile.interactable(team);
    }
}
