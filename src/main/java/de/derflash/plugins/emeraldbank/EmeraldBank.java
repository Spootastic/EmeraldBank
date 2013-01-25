package de.derflash.plugins.emeraldbank;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;

public class EmeraldBank extends JavaPlugin implements Listener {

	static EmeraldBank p;

    private YamlConfiguration language = null;

	HashMap<BankPlayer, Sign> bankUsers = new HashMap<BankPlayer, Sign>();
	HashMap<String, Location> moveCheck = new HashMap<String, Location>();
	
	
	Economy _economy = null;
	public Economy economy() {
		if (_economy == null) {
			if (getServer().getPluginManager().getPlugin("Vault") != null) {
	            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
	            if (rsp != null) {
	                _economy = rsp.getProvider();
	                this.getLogger().info("Connected to " + _economy.getName() + " for economy support.");
	            } else {
	                this.getLogger().warning("Vault could not find any economy plugin to connect to. Please install one or disable economy.");
	            }
	        } else {
	            this.getLogger().warning("Coult not find Vault plugin, but economy is enabled. Please install Vault or disable economy.");
	        }
		}
		return _economy;
	}
	
	
    public void onEnable() {
		try {
		    Metrics metrics = new Metrics(this);
		    metrics.start();
		} catch (IOException e) {
		    // Failed to submit the stats :-(
		}
		
    	if (economy() == null) {
            this.getLogger().warning("Disabling EmeraldBank plugin...");
    		this.setEnabled(false);
    	}
    	
    	saveDefaultConfig();
    	
        String _lang = getConfig().getString("language", "en");
        InputStream languageStream = getResource(_lang + ".lang");
        if (languageStream == null) {
            languageStream = getResource("en.lang");
        }
        language = YamlConfiguration.loadConfiguration(languageStream);
        if (language == null) {
            System.out.println(this + " could not load its translations! Disabling...");
            getPluginLoader().disablePlugin(this);
            return;
        }
    	
    	EmeraldBank.p = this;
    	
        getServer().getPluginManager().registerEvents(this, this);
    }

    public void onDisable() {
		for (Sign sign : bankUsers.values()) {
			updateSignState(sign, 0);
		}
		
		bankUsers.clear();
    	moveCheck.clear();
		
		_economy = null;
    }
    
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		BankPlayer bPlayer = bankPlayerForPlayer(event.getPlayer());
		
		if (bPlayer != null) {
			updateSignState(bPlayer.getSign(), 0);
			bankUsers.remove(bPlayer);
			moveCheck.remove(event.getPlayer().getName());
		}
	}
	
	
	@EventHandler
	public void onSignChange (SignChangeEvent event) {
		if (event.getLine(0).startsWith("Emerald Bank")) {
	    	if (!event.getPlayer().hasPermission("ebank.admin")) {
	    		event.getPlayer().sendMessage(ChatColor.DARK_RED + this.translate("createFail"));
	    		event.setCancelled(true);
	    		return;
	    	}
	    	
	    	event.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
	    	event.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
	    	event.setLine(2, " " + ChatColor.GREEN + this.translate("bank"));
	    	event.setLine(3, " " + ChatColor.GREEN + this.translate("created"));

		}
	}
	
	
//	@EventHandler(priority = EventPriority.LOW)
	
	@EventHandler
	public void onPlayerInteract (PlayerInteractEvent event) {
    	Block signBlock = event.getClickedBlock();
    	if (signBlock == null) return;
    	
    	if (signBlock.getTypeId() != 68) return;
		Sign sign = (Sign)signBlock.getState();

		if (!sign.getLine(0).equals(" " + ChatColor.AQUA + "Emerald Bank")) return;
		
		Player player = event.getPlayer();
		
		if (player.hasPermission("ebank.admin") && player.isSneaking()) {
			return;
		}
		event.setCancelled(true);
		
		BankPlayer bPlayer = null;
		
		if (isOpenBank(sign)) {
			if (bankPlayerForPlayer(player) != null) {
		    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + this.translate("openedAnotherOne"));
				return;
			}
			
			bPlayer = new BankPlayer(player, sign);

	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + this.translate("welcome"));
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "~~~~~~~~~~~~~~~~~~~~~~");
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + this.translate("balance") + ": " + economy().format(economy().getBalance(player.getName())));

	    	PlayerInventory pInv = player.getInventory();
	    	int es = 0;
	    	for (ItemStack stack : pInv.all(Material.EMERALD_BLOCK).values()) {
	    		es += (stack.getAmount() * 9);
	    	}
	    	for (ItemStack stack : pInv.all(Material.EMERALD).values()) {
	    		es += stack.getAmount();
	    	}
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + this.translate("inInv") + ": " + economy().format(es));

	    	bankUsers.put(bPlayer, sign);
	    	moveCheck.put(player.getName(), signBlock.getLocation());

		} else {
			BankPlayer _bPlayer = bankPlayerForSign(sign);
			if (_bPlayer.getPlayer().equals(player)) bPlayer = _bPlayer;
		}
		
		if (bPlayer != null) {
			switchBankState(bPlayer);
		} else {
	    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + this.translate("goAway"));

		}
		
    }
	
	@EventHandler
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		final Player player = event.getPlayer();
				
		final BankPlayer bPlayer = bankPlayerForPlayer(player);
		if (bPlayer == null) return;
		
		final String _message = event.getMessage();
		if (bPlayer.getTransferTemp() > 0) {
			
			// event fits, we do not need to put anything into the chat
			event.setCancelled(true);
			
			p.getServer().getScheduler().scheduleSyncDelayedTask(p, new Runnable() { public void run() {
				
				if (_message.equals("ok") && bPlayer.getTransferPlayer() != null) {
					
					Player toPlayer = getServer().getPlayer(bPlayer.getTransferPlayer());
					if (toPlayer == null || !toPlayer.isOnline()) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("noLongerOnline"));
						return;
					}
					
					EconomyResponse resp = economy().withdrawPlayer(player.getName(), bPlayer.getTransferTemp());
					if (!resp.transactionSuccess()) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("notEnough", new String[] {"currency", economy().currencyNamePlural()}));
						return;
					}

					resp = economy().depositPlayer(bPlayer.getTransferPlayer(), bPlayer.getTransferTemp());
					if (!resp.transactionSuccess()) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("transferFail", new String[] {"error", resp.errorMessage}));
						economy().depositPlayer(player.getName(), bPlayer.getTransferTemp());
						return;
					}
					
			    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("transferOk"));
			    	toPlayer.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("hasSend", new String[] {"sender", player.getDisplayName(), "amount", economy().format(bPlayer.getTransferTemp())}));

					bPlayer.setTransferTemp(0);
					bPlayer.setTransferPlayer(null);

				} else {
					Player toPlayer = getServer().getPlayer(_message);
					if (toPlayer == null || !toPlayer.isOnline()) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("notOnline"));
				    	return;
					}
					
					bPlayer.setTransferPlayer(toPlayer.getName());
			    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("transferConfirm", new String[] {"amount", economy().format(bPlayer.getTransferTemp()), "to", toPlayer.getName()}));
					
				}
				
			}}, 0L);



			
		} else {
			
			boolean inBlocks = false;
			
			String message = _message.trim();
			if (message.endsWith("b")) {
				message = message.substring(0, message.length()-1).trim();
				inBlocks = true;
			}
			
			int _messageValue = 0;
			try {
				_messageValue = Integer.parseInt(message);
			} catch (Exception e) {}
			
			if (_messageValue == 0) return;
			
			if (inBlocks) _messageValue *= 9;
			
			// event fits, we do not need to put anything into the chat
			event.setCancelled(true);
			
			final int messageValue = _messageValue;
			p.getServer().getScheduler().scheduleSyncDelayedTask(p, new Runnable() { public void run() {
				
				switch (bPlayer.getState()) {
				
				case 1: {
					EconomyResponse resp = economy().withdrawPlayer(player.getName(), messageValue);
					if (resp.transactionSuccess()) {
						SmartAmount sa = new SmartAmount(messageValue);
						PlayerInventory pInv = player.getInventory();

						HashMap<Integer, ItemStack> notAdded;
						if (sa.getEmeraldBlocks() > 0 && sa.getEmeralds() > 0) {
							notAdded = pInv.addItem(new ItemStack(Material.EMERALD_BLOCK, (int)sa.getEmeraldBlocks()), new ItemStack(Material.EMERALD, (int)sa.getEmeralds()));
							
						} else if (sa.getEmeraldBlocks() > 0) {
							notAdded = pInv.addItem(new ItemStack(Material.EMERALD_BLOCK, (int)sa.getEmeraldBlocks()));
							
						} else {
							notAdded = pInv.addItem(new ItemStack(Material.EMERALD, (int)sa.getEmeralds()));
						}
											
						if (!notAdded.isEmpty()) {
							for (ItemStack notFit : notAdded.values()) {
						    	
								if (notFit.getType().equals(Material.EMERALD_BLOCK)) {
									sa.setEmeraldBlocks(sa.getEmeraldBlocks() - notFit.getAmount());
									economy().depositPlayer(player.getName(), notFit.getAmount() * 9);
									
								} else if (notFit.getType().equals(Material.EMERALD)) {						    	
									sa.setEmeralds(sa.getEmeralds() - notFit.getAmount());
									economy().depositPlayer(player.getName(), notFit.getAmount());
									
								}
							}
							
							if (sa.getAmount() > 0) {
						    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("notEnoughPart"));
						    	
							} else {
						    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("notEnoughAbort"));
						    	return;
						    	
							}
							 
						}
						
				    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("payedOut") + ": " + sa.getAmountAsString());

					} else {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("notEnoughToRedeem", new String[] {"currency", economy().currencyNamePlural()}));
		
					}
						
				} break;
				
				case 2: {
					int amountCheck = messageValue;
					int blocksFound = 0;
					int emeraldsFound = 0;
					
					PlayerInventory inv = player.getInventory();

					HashMap<Integer, ? extends ItemStack> emeraldBlocks = inv.all(Material.EMERALD_BLOCK);
					for (ItemStack emeraldBlockStack : emeraldBlocks.values()) {
						int amount = emeraldBlockStack.getAmount();

						while (amountCheck > 0 && amount > 0) {
							
							if (amountCheck >= 9) {
								blocksFound++;
								amount--;
								amountCheck -= 9;
								
							} else {
								
								// only use swapping if there are not enough emeralds
								if (!inv.contains(new ItemStack(Material.EMERALD), amountCheck)) {
									int needToAdd = 9 - amountCheck;
									
									HashMap<Integer, ? extends ItemStack> tausch = inv.addItem(new ItemStack(Material.EMERALD, needToAdd));
									if (!tausch.isEmpty()) {
										// undo
										ItemStack tauschLeft = tausch.values().iterator().next();
										inv.removeItem(new ItemStack(Material.EMERALD, needToAdd - tauschLeft.getAmount()));
										
								    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("needMoreSpace"));
								    	return;
									}
									
									inv.removeItem(new ItemStack(Material.EMERALD_BLOCK, 1));
									
									amountCheck = 0;
									amount--; // just to be sure
									
								}
								
								break;

							}
							
						}
						
					}
					
					if (amountCheck > 0) {
						HashMap<Integer, ? extends ItemStack> emeralds = inv.all(Material.EMERALD);
						for (ItemStack emeraldStack : emeralds.values()) {
							int amount = emeraldStack.getAmount();
							while (amountCheck > 0 && amount > 0) {
								emeraldsFound++;
								amount--;
								amountCheck--;
							}
							
							if (amountCheck == 0) break;
						}
					}
					
					if (amountCheck > 0) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("notEnoughToDeposit", new String[] {"amount", new SmartAmount(amountCheck).getAmountAsString(), "currency", economy().currencyNamePlural()}));

					} else {
						if (blocksFound > 0) inv.removeItem(new ItemStack(Material.EMERALD_BLOCK, blocksFound));
						if (emeraldsFound > 0) inv.removeItem(new ItemStack(Material.EMERALD, emeraldsFound));
						
						player.updateInventory();
						
						economy().depositPlayer(player.getName(), messageValue);
				    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("depositOk") + ": " + new SmartAmount(messageValue).getAmountAsString());
					}
					
									
				} break;
				
				case 3: {
					if (!economy().has(player.getName(), messageValue)) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + translate("notEnoughToTransfer", new String[] {"currency", economy().currencyNamePlural()}));
				    	return;
					}
					
					bPlayer.setTransferTemp(messageValue);
			    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("transferQuery", new String[] {"amount", economy().format(messageValue)}));

				} break;

				}				
				
			}}, 0L);

			
		}
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ())
        {

        	if (moveCheck.isEmpty()) return;
    		if (!moveCheck.containsKey(event.getPlayer().getName())) return;
    		
    		Player player = event.getPlayer();
    		Location pLoc = player.getLocation();
    		Location cLoc = moveCheck.get(player.getName());
    		
    		if (!pLoc.getWorld().equals(cLoc.getWorld()) || (pLoc.distance(cLoc) > 4 && event.getFrom().distance(event.getTo()) > 0) ) {
    			BankPlayer bPlayer = bankPlayerForPlayer(player);
    			if (bPlayer != null) closeBankForPlayer(bPlayer);
    			
    			moveCheck.remove(player.getName());
    		}

        }
        
	}
	
	private boolean isOpenBank(Sign sign) {
		return !bankUsers.containsValue(sign);
	}
	
	private BankPlayer bankPlayerForSign(Sign sign) {
		for (BankPlayer p : bankUsers.keySet()) {
			if (!p.getPlayer().isOnline()) closeBankForPlayer(p);
			else if (p.getSign().equals(sign)) return p;
		}
		return null;
	}
	
	private BankPlayer bankPlayerForPlayer(Player player) {
		for (BankPlayer p : bankUsers.keySet()) {
			if (p.getPlayer().equals(player)) return p;
		}
		return null;
	}
	
	private void switchBankState(BankPlayer bPlayer) {
		int state = bPlayer.getState();
		Player player = bPlayer.getPlayer();
		Sign sign = bPlayer.getSign();
		
		// switch state
		state++; if (state > 3) state = 1;
		
		// reset
		bPlayer.setTransferTemp(0);
		bPlayer.setTransferPlayer(null);

		switch (state) {

		case 1: {
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "~~~~~~~~~~~~~~~~~~~~~~");
			player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("msgWithdraw", new String[] {"currency", economy().currencyNamePlural()}));
		} break;

		case 2: {
				player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "~~~~~~~~~~~~~~~~~~~~~~");
				player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("msgDeposit", new String[] {"currency", economy().currencyNamePlural()}));
		} break;

		case 3: {
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "~~~~~~~~~~~~~~~~~~~~~~");
			player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("msgTransfer", new String[] {"currency", economy().currencyNamePlural()}));
		} break;

		}
		
		bPlayer.setState(state);
		updateSignState(sign, state);
	}
	
	private void closeBankForPlayer(BankPlayer bPlayer) {
		Player player = bPlayer.getPlayer();
		Sign sign = bPlayer.getSign();
		
		if (player.isOnline()) player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + translate("goodbye"));
		updateSignState(sign, 0);
		
    	bankUsers.remove(bPlayer);
	}

	private void updateSignState(Sign sign, int state) {
		switch (state) {

		case 0: {
			sign.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
			sign.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
			sign.setLine(2, " " + ChatColor.GREEN + translate("counter"));
			sign.setLine(3, " " + ChatColor.GREEN + translate("open"));
		} break;

		case 1: {
			sign.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
			sign.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
			sign.setLine(2, " " + ChatColor.DARK_RED + translate("drawo"));
			sign.setLine(3, " " + ChatColor.DARK_RED + translate("utSomething"));
		} break;

		case 2: {
			sign.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
			sign.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
			sign.setLine(2, " " + ChatColor.DARK_RED + translate("depo"));
			sign.setLine(3, " " + ChatColor.DARK_RED + translate("sitSomething"));
		} break;

		case 3: {
			sign.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
			sign.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
			sign.setLine(2, " " + ChatColor.DARK_RED + translate("transf"));
			sign.setLine(3, " " + ChatColor.DARK_RED + translate("erSomething"));
		} break;

		}
		
		sign.update(true);
	}
	
	
	// translation stuff
	
    public String translate(String id) {
        String out = language.getString(id);
        if (out != null) {
            return out;
        } else {
            return "MissingTranslation: " + id;
        }
    }

    public String translate(String id, String[] more) {
    	if (more.length % 2 == 1) {
            return "FailedTranslation: " + id;
        } // check even

        String out = language.getString(id);
        if (out == null) {
            return "MissingTranslation: " + id;
        }

        for(int a = 0; a < more.length; a++){
            String label = more[a];
            String value = more[a+1];
            out = out.replaceAll("%"+label+"%", value);
            a++;
        }
        return out;
    }
	
}

