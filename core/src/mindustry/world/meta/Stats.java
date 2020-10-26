package mindustry.world.meta;

import arc.func.*;
import arc.struct.ObjectMap.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.values.*;

/** Hold and organizes a list of block stats. */
public class Stats{
    /** Whether to display stats with categories. If false, categories are completely ignored during display. */
    public boolean useCategories = false;
    /** Whether these stats are initialized yet. */
    public boolean intialized = false;

    @Nullable
    private OrderedMap<StatCat, OrderedMap<Stat, Seq<StatValue>>> map;
    private boolean dirty;

    /** Adds a single float value with this stat, formatted to 2 decimal places. */
    public void add(Stat stat, float value, StatUnit unit){
        add(stat, new NumberValue(value, unit));
    }

    /** Adds a single float value with this stat and no unit. */
    public void add(Stat stat, float value){
        add(stat, value, StatUnit.none);
    }

    /** Adds an integer percent stat value. Value is assumed to be in the 0-1 range. */
    public void addPercent(Stat stat, float value){
        add(stat, new NumberValue((int)(value * 100), StatUnit.percent));
    }

    /** Adds a single y/n boolean value. */
    public void add(Stat stat, boolean value){
        add(stat, new BooleanValue(value));
    }

    /** Adds an item value. */
    public void add(Stat stat, Item item){
        add(stat, new ItemListValue(new ItemStack(item, 1)));
    }

    /** Adds an item value. */
    public void add(Stat stat, ItemStack item){
        add(stat, new ItemListValue(item));
    }

    /** Adds an item value. */
    public void add(Stat stat, Liquid liquid, float amount, boolean perSecond){
        add(stat, new LiquidValue(liquid, amount, perSecond));
    }

    public void add(Stat stat, Attribute attr){
        add(stat, attr, false, 1f);
    }

    public void add(Stat stat, Attribute attr, float scale){
        add(stat, attr, false, scale);
    }

    public void add(Stat stat, Attribute attr, boolean floating){
        add(stat, attr, floating, 1f);
    }

    public void add(Stat stat, Attribute attr, boolean floating, float scale){
        Seq<FloorEfficiencyValue> attributeTiles = new Seq<FloorEfficiencyValue>();
        for(Block block : Vars.content.blocks()){
            if(!block.isFloor() || block.asFloor().attributes.get(attr) == 0 || (block.asFloor().isLiquid && !floating)) continue;
            attributeTiles.add(new FloorEfficiencyValue(block.asFloor(), block.asFloor().attributes.get(attr) * scale));
        }

        attributeTiles.sort(new Floatf<FloorEfficiencyValue>(){
            public float get(FloorEfficiencyValue value){
                return value.multiplier;
            }
        });
        
        for(FloorEfficiencyValue value : attributeTiles) {
            add(stat, value);
        }
    }

    /** Adds a single string value with this stat. */
    public void add(Stat stat, String format, Object... args){
        add(stat, new StringValue(format, args));
    }

    /** Adds a stat value. */
    public void add(Stat stat, StatValue value){
        if(map == null) map = new OrderedMap<>();

        if(!map.containsKey(stat.category)){
            map.put(stat.category, new OrderedMap<>());
        }

        map.get(stat.category).get(stat, Seq::new).add(value);

        dirty = true;
    }

    /** Removes a stat, if it exists. */
    public void remove(Stat stat){
        if(map == null) map = new OrderedMap<>();

        if(!map.containsKey(stat.category) || !map.get(stat.category).containsKey(stat)){
            throw new RuntimeException("No stat entry found: \"" + stat + "\" in block.");
        }

        map.get(stat.category).remove(stat);

        dirty = true;
    }

    public OrderedMap<StatCat, OrderedMap<Stat, Seq<StatValue>>> toMap(){
        if(map == null) map = new OrderedMap<>();

        //sort stats by index if they've been modified
        if(dirty){
            map.orderedKeys().sort();
            for(Entry<StatCat, OrderedMap<Stat, Seq<StatValue>>> entry : map.entries()){
                entry.value.orderedKeys().sort();
            }

            dirty = false;
        }
        return map;
    }
}
