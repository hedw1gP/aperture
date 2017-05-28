package mchorse.aperture.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Copied class {@link net.minecraft.command.EntitySelector} because vanilla
 * EntitySelector doesn't work on client side.
 *
 * This is only going to be used on the client side
 */
@SideOnly(Side.CLIENT)
public class EntitySelector
{
    /** This matches the at-tokens introduced for command blocks, including their arguments, if any. */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^@([pare])(?:\\[([\\w\\.:=,!-]*)\\])?$"); // FORGE: allow '.' and ':' in entity selectors
    /** This matches things like "-1,,4", and is used for getting x,y,z,range from the token's argument list. */
    private static final Pattern INT_LIST_PATTERN = Pattern.compile("\\G([-!]?[\\w-]*)(?:$|,)");
    /** This matches things like "rm=4,c=2" and is used for handling named token arguments. */
    private static final Pattern KEY_VALUE_LIST_PATTERN = Pattern.compile("\\G([\\w:]+)=([-!]?[\\w\\.-]*)(?:$|,)"); // FORGE: allow ':' in arguments and '.' in value of entity selectors
    private static final Set<String> WORLD_BINDING_ARGS = Sets.newHashSet(new String[] {"x", "y", "z", "dx", "dy", "dz", "rm", "r"});

    @Nullable
    public static <T extends Entity> T matchOneEntity(ICommandSender sender, World world, String token, Class<? extends T> targetClass)
    {
        List<T> list = matchEntities(sender, world, token, targetClass);
        return (T) (list.size() >= 1 ? (Entity) list.get(0) : null);
    }

    public static <T extends Entity> List<T> matchEntities(ICommandSender sender, World world, String token, Class<? extends T> targetClass)
    {
        Matcher matcher = TOKEN_PATTERN.matcher(token);

        if (matcher.matches() && sender.canCommandSenderUseCommand(1, "@"))
        {
            Map<String, String> map = getArgumentMap(matcher.group(2));

            if (!isEntityTypeValid(sender, map))
            {
                return Collections.<T> emptyList();
            }
            else
            {
                String s = matcher.group(1);
                BlockPos blockpos = getBlockPosFromArguments(map, sender.getPosition());
                Vec3d vec3d = getPosFromArguments(map, sender.getPositionVector());
                List<T> list1 = Lists.<T> newArrayList();

                if (world != null)
                {
                    List<Predicate<Entity>> list2 = Lists.<Predicate<Entity>> newArrayList();
                    list2.addAll(getTypePredicates(map, s));
                    list2.addAll(getXpLevelPredicates(map));
                    list2.addAll(getGamemodePredicates(map));
                    list2.addAll(getTeamPredicates(map));
                    list2.addAll(getNamePredicates(map));
                    list2.addAll(getTagPredicates(map));
                    list2.addAll(getRadiusPredicates(map, vec3d));
                    list2.addAll(getRotationsPredicates(map));
                    list2.addAll(net.minecraftforge.event.ForgeEventFactory.gatherEntitySelectors(map, s, sender, vec3d));
                    list1.addAll(filterResults(map, targetClass, list2, s, world, blockpos));
                }

                return getEntitiesFromPredicates(list1, map, sender, targetClass, s, vec3d);
            }
        }
        else
        {
            return Collections.<T> emptyList();
        }
    }

    private static List<World> getWorlds(ICommandSender sender, Map<String, String> argumentMap)
    {
        List<World> list = Lists.<World> newArrayList();

        if (hasArgument(argumentMap))
        {
            list.add(sender.getEntityWorld());
        }
        else
        {
            Collections.addAll(list, sender.getServer().worldServers);
        }

        return list;
    }

    private static <T extends Entity> boolean isEntityTypeValid(ICommandSender commandSender, Map<String, String> params)
    {
        String s = getArgument(params, "type");
        s = s != null && s.startsWith("!") ? s.substring(1) : s;

        if (s != null && !EntityList.isStringValidEntityName(s))
        {
            TextComponentTranslation textcomponenttranslation = new TextComponentTranslation("commands.generic.entity.invalidType", new Object[] {s});
            textcomponenttranslation.getStyle().setColor(TextFormatting.RED);
            commandSender.addChatMessage(textcomponenttranslation);
            return false;
        }
        else
        {
            return true;
        }
    }

    private static List<Predicate<Entity>> getTypePredicates(Map<String, String> params, String type)
    {
        List<Predicate<Entity>> list = Lists.<Predicate<Entity>> newArrayList();
        String s = getArgument(params, "type");
        final boolean flag = s != null && s.startsWith("!");

        if (flag)
        {
            s = s.substring(1);
        }

        boolean flag1 = !type.equals("e");
        boolean flag2 = type.equals("r") && s != null;

        if ((s == null || !type.equals("e")) && !flag2)
        {
            if (flag1)
            {
                list.add(new Predicate<Entity>()
                {
                    @Override
                    public boolean apply(@Nullable Entity p_apply_1_)
                    {
                        return p_apply_1_ instanceof EntityPlayer;
                    }
                });
            }
        }
        else
        {
            final String s_f = s;
            list.add(new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    return EntityList.isStringEntityName(p_apply_1_, s_f) != flag;
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getXpLevelPredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = Lists.<Predicate<Entity>> newArrayList();
        final int i = parseIntWithDefault(params, "lm", -1);
        final int j = parseIntWithDefault(params, "l", -1);

        if (i > -1 || j > -1)
        {
            list.add(new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    if (!(p_apply_1_ instanceof EntityPlayerMP))
                    {
                        return false;
                    }
                    else
                    {
                        EntityPlayerMP entityplayermp = (EntityPlayerMP) p_apply_1_;
                        return (i <= -1 || entityplayermp.experienceLevel >= i) && (j <= -1 || entityplayermp.experienceLevel <= j);
                    }
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getGamemodePredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = Lists.<Predicate<Entity>> newArrayList();
        String s = getArgument(params, "m");

        if (s == null)
        {
            return list;
        }
        else
        {
            final boolean flag = s.startsWith("!");

            if (flag)
            {
                s = s.substring(1);
            }

            GameType gametype;

            try
            {
                int i = Integer.parseInt(s);
                gametype = GameType.parseGameTypeWithDefault(i, GameType.NOT_SET);
            }
            catch (Throwable var6)
            {
                gametype = GameType.parseGameTypeWithDefault(s, GameType.NOT_SET);
            }

            final GameType type = gametype;
            list.add(new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    if (!(p_apply_1_ instanceof EntityPlayerMP))
                    {
                        return false;
                    }
                    else
                    {
                        EntityPlayerMP entityplayermp = (EntityPlayerMP) p_apply_1_;
                        GameType gametype1 = entityplayermp.interactionManager.getGameType();
                        return flag ? gametype1 != type : gametype1 == type;
                    }
                }
            });
            return list;
        }
    }

    private static List<Predicate<Entity>> getTeamPredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = Lists.<Predicate<Entity>> newArrayList();
        String s = getArgument(params, "team");
        final boolean flag = s != null && s.startsWith("!");

        if (flag)
        {
            s = s.substring(1);
        }

        if (s != null)
        {
            final String s_f = s;
            list.add(new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    if (!(p_apply_1_ instanceof EntityLivingBase))
                    {
                        return false;
                    }
                    else
                    {
                        EntityLivingBase entitylivingbase = (EntityLivingBase) p_apply_1_;
                        Team team = entitylivingbase.getTeam();
                        String s1 = team == null ? "" : team.getRegisteredName();
                        return s1.equals(s_f) != flag;
                    }
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getNamePredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = Lists.<Predicate<Entity>> newArrayList();
        String s = getArgument(params, "name");
        final boolean flag = s != null && s.startsWith("!");

        if (flag)
        {
            s = s.substring(1);
        }

        if (s != null)
        {
            final String s_f = s;
            list.add(new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    return p_apply_1_ != null && p_apply_1_.getName().equals(s_f) != flag;
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getTagPredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = Lists.<Predicate<Entity>> newArrayList();
        String s = getArgument(params, "tag");
        final boolean flag = s != null && s.startsWith("!");

        if (flag)
        {
            s = s.substring(1);
        }

        if (s != null)
        {
            final String s_f = s;
            list.add(new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    return p_apply_1_ == null ? false : ("".equals(s_f) ? p_apply_1_.getTags().isEmpty() != flag : p_apply_1_.getTags().contains(s_f) != flag);
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getRadiusPredicates(Map<String, String> params, final Vec3d pos)
    {
        double d0 = parseIntWithDefault(params, "rm", -1);
        double d1 = parseIntWithDefault(params, "r", -1);
        final boolean flag = d0 < -0.5D;
        final boolean flag1 = d1 < -0.5D;

        if (flag && flag1)
        {
            return Collections.<Predicate<Entity>> emptyList();
        }
        else
        {
            double d2 = Math.max(d0, 1.0E-4D);
            final double d3 = d2 * d2;
            double d4 = Math.max(d1, 1.0E-4D);
            final double d5 = d4 * d4;
            return Lists.<Predicate<Entity>> newArrayList(new Predicate[] {new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    if (p_apply_1_ == null)
                    {
                        return false;
                    }
                    else
                    {
                        double d6 = pos.squareDistanceTo(p_apply_1_.posX, p_apply_1_.posY, p_apply_1_.posZ);
                        return (flag || d6 >= d3) && (flag1 || d6 <= d5);
                    }
                }
            }
            });
        }
    }

    private static List<Predicate<Entity>> getRotationsPredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = Lists.<Predicate<Entity>> newArrayList();

        if (params.containsKey("rym") || params.containsKey("ry"))
        {
            final int i = MathHelper.clampAngle(parseIntWithDefault(params, "rym", 0));
            final int j = MathHelper.clampAngle(parseIntWithDefault(params, "ry", 359));
            list.add(new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    if (p_apply_1_ == null)
                    {
                        return false;
                    }
                    else
                    {
                        int i1 = MathHelper.clampAngle(MathHelper.floor_float(p_apply_1_.rotationYaw));
                        return i > j ? i1 >= i || i1 <= j : i1 >= i && i1 <= j;
                    }
                }
            });
        }

        if (params.containsKey("rxm") || params.containsKey("rx"))
        {
            final int k = MathHelper.clampAngle(parseIntWithDefault(params, "rxm", 0));
            final int l = MathHelper.clampAngle(parseIntWithDefault(params, "rx", 359));
            list.add(new Predicate<Entity>()
            {
                @Override
                public boolean apply(@Nullable Entity p_apply_1_)
                {
                    if (p_apply_1_ == null)
                    {
                        return false;
                    }
                    else
                    {
                        int i1 = MathHelper.clampAngle(MathHelper.floor_float(p_apply_1_.rotationPitch));
                        return k > l ? i1 >= k || i1 <= l : i1 >= k && i1 <= l;
                    }
                }
            });
        }

        return list;
    }

    private static <T extends Entity> List<T> filterResults(Map<String, String> params, Class<? extends T> entityClass, List<Predicate<Entity>> inputList, String type, World worldIn, BlockPos position)
    {
        List<T> list = Lists.<T> newArrayList();
        String s = getArgument(params, "type");
        s = s != null && s.startsWith("!") ? s.substring(1) : s;
        boolean flag = !type.equals("e");
        boolean flag1 = type.equals("r") && s != null;
        int i = parseIntWithDefault(params, "dx", 0);
        int j = parseIntWithDefault(params, "dy", 0);
        int k = parseIntWithDefault(params, "dz", 0);
        int l = parseIntWithDefault(params, "r", -1);
        Predicate<Entity> predicate = Predicates.and(inputList);
        Predicate<Entity> predicate1 = Predicates.<Entity> and(EntitySelectors.IS_ALIVE, predicate);
        int i1 = worldIn.playerEntities.size();
        int j1 = worldIn.loadedEntityList.size();
        boolean flag2 = i1 < j1 / 16;

        if (!params.containsKey("dx") && !params.containsKey("dy") && !params.containsKey("dz"))
        {
            if (l >= 0)
            {
                AxisAlignedBB axisalignedbb1 = new AxisAlignedBB(position.getX() - l, position.getY() - l, position.getZ() - l, position.getX() + l + 1, position.getY() + l + 1, position.getZ() + l + 1);

                if (flag && flag2 && !flag1)
                {
                    list.addAll(worldIn.<T> getPlayers(entityClass, predicate1));
                }
                else
                {
                    list.addAll(worldIn.<T> getEntitiesWithinAABB(entityClass, axisalignedbb1, predicate1));
                }
            }
            else if (type.equals("a"))
            {
                list.addAll(worldIn.<T> getPlayers(entityClass, predicate));
            }
            else if (!type.equals("p") && (!type.equals("r") || flag1))
            {
                list.addAll(worldIn.<T> getEntities(entityClass, predicate1));
            }
            else
            {
                list.addAll(worldIn.<T> getPlayers(entityClass, predicate1));
            }
        }
        else
        {
            final AxisAlignedBB axisalignedbb = getAABB(position, i, j, k);

            if (flag && flag2 && !flag1)
            {
                Predicate<Entity> predicate2 = new Predicate<Entity>()
                {
                    @Override
                    public boolean apply(@Nullable Entity p_apply_1_)
                    {
                        return p_apply_1_ != null && axisalignedbb.intersectsWith(p_apply_1_.getEntityBoundingBox());
                    }
                };
                list.addAll(worldIn.<T> getPlayers(entityClass, Predicates.<T> and(predicate1, predicate2)));
            }
            else
            {
                list.addAll(worldIn.<T> getEntitiesWithinAABB(entityClass, axisalignedbb, predicate1));
            }
        }

        return list;
    }

    private static <T extends Entity> List<T> getEntitiesFromPredicates(List<T> matchingEntities, Map<String, String> params, ICommandSender sender, Class<? extends T> targetClass, String type, final Vec3d pos)
    {
        int i = parseIntWithDefault(params, "c", !type.equals("a") && !type.equals("e") ? 1 : 0);

        if (!type.equals("p") && !type.equals("a") && !type.equals("e"))
        {
            if (type.equals("r"))
            {
                Collections.shuffle(matchingEntities);
            }
        }
        else
        {
            Collections.sort(matchingEntities, new Comparator<Entity>()
            {
                @Override
                public int compare(Entity p_compare_1_, Entity p_compare_2_)
                {
                    return ComparisonChain.start().compare(p_compare_1_.getDistanceSq(pos.xCoord, pos.yCoord, pos.zCoord), p_compare_2_.getDistanceSq(pos.xCoord, pos.yCoord, pos.zCoord)).result();
                }
            });
        }

        Entity entity = sender.getCommandSenderEntity();

        if (entity != null && targetClass.isAssignableFrom(entity.getClass()) && i == 1 && ((List) matchingEntities).contains(entity) && !"r".equals(type))
        {
            matchingEntities = Lists.newArrayList((T) entity);
        }

        if (i != 0)
        {
            if (i < 0)
            {
                Collections.reverse(matchingEntities);
            }

            matchingEntities = ((List) matchingEntities).subList(0, Math.min(Math.abs(i), ((List) matchingEntities).size()));
        }

        return matchingEntities;
    }

    private static AxisAlignedBB getAABB(BlockPos pos, int x, int y, int z)
    {
        boolean flag = x < 0;
        boolean flag1 = y < 0;
        boolean flag2 = z < 0;
        int i = pos.getX() + (flag ? x : 0);
        int j = pos.getY() + (flag1 ? y : 0);
        int k = pos.getZ() + (flag2 ? z : 0);
        int l = pos.getX() + (flag ? 0 : x) + 1;
        int i1 = pos.getY() + (flag1 ? 0 : y) + 1;
        int j1 = pos.getZ() + (flag2 ? 0 : z) + 1;
        return new AxisAlignedBB(i, j, k, l, i1, j1);
    }

    private static BlockPos getBlockPosFromArguments(Map<String, String> params, BlockPos pos)
    {
        return new BlockPos(parseIntWithDefault(params, "x", pos.getX()), parseIntWithDefault(params, "y", pos.getY()), parseIntWithDefault(params, "z", pos.getZ()));
    }

    private static Vec3d getPosFromArguments(Map<String, String> params, Vec3d pos)
    {
        return new Vec3d(getCoordinate(params, "x", pos.xCoord, true), getCoordinate(params, "y", pos.yCoord, false), getCoordinate(params, "z", pos.zCoord, true));
    }

    private static double getCoordinate(Map<String, String> params, String key, double defaultD, boolean offset)
    {
        return params.containsKey(key) ? MathHelper.parseIntWithDefault(params.get(key), MathHelper.floor_double(defaultD)) + (offset ? 0.5D : 0.0D) : defaultD;
    }

    private static boolean hasArgument(Map<String, String> params)
    {
        for (String s : WORLD_BINDING_ARGS)
        {
            if (params.containsKey(s))
            {
                return true;
            }
        }

        return false;
    }

    private static int parseIntWithDefault(Map<String, String> params, String key, int defaultI)
    {
        return params.containsKey(key) ? MathHelper.parseIntWithDefault(params.get(key), defaultI) : defaultI;
    }

    @Nullable
    private static String getArgument(Map<String, String> params, String key)
    {
        return params.get(key);
    }

    private static Map<String, String> getArgumentMap(@Nullable String argumentString)
    {
        Map<String, String> map = Maps.<String, String> newHashMap();

        if (argumentString == null)
        {
            return map;
        }
        else
        {
            int i = 0;
            int j = -1;

            for (Matcher matcher = INT_LIST_PATTERN.matcher(argumentString); matcher.find(); j = matcher.end())
            {
                String s = null;

                switch (i++)
                {
                    case 0:
                        s = "x";
                        break;
                    case 1:
                        s = "y";
                        break;
                    case 2:
                        s = "z";
                        break;
                    case 3:
                        s = "r";
                }

                if (s != null && !matcher.group(1).isEmpty())
                {
                    map.put(s, matcher.group(1));
                }
            }

            if (j < argumentString.length())
            {
                Matcher matcher1 = KEY_VALUE_LIST_PATTERN.matcher(j == -1 ? argumentString : argumentString.substring(j));

                while (matcher1.find())
                {
                    map.put(matcher1.group(1), matcher1.group(2));
                }
            }

            return map;
        }
    }
}