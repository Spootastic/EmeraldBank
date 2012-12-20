package de.derflash.plugins.emeraldbank;

import java.util.HashMap;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
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

public class EmeraldBank extends JavaPlugin implements Listener {

	static EmeraldBank p;

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
    	if (economy() == null) {
            this.getLogger().warning("Disabling EmeraldBank plugin...");
    		this.setEnabled(false);
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
	    		event.getPlayer().sendMessage(ChatColor.DARK_RED + "Dir ist es nicht erlaubt, eine eigene Bank zu erstellen");
	    		event.setCancelled(true);
	    		return;
	    	}
	    	
	    	event.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
	    	event.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
	    	event.setLine(2, " " + ChatColor.GREEN + "Bank");
	    	event.setLine(3, " " + ChatColor.GREEN + "erstellt!");

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
		    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Reicht dir denn ein Bankschalter nicht aus?");
				return;
			}
			
			bPlayer = new BankPlayer(player, sign);

	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Willkommen bei der Emerald Bank!");
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "~~~~~~~~~~~~~~~~~~~~~~");
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Kontostand: " + economy().format(economy().getBalance(player.getName())));

	    	PlayerInventory pInv = player.getInventory();
	    	int es = 0;
	    	for (ItemStack stack : pInv.all(Material.EMERALD_BLOCK).values()) {
	    		es += (stack.getAmount() * 9);
	    	}
	    	for (ItemStack stack : pInv.all(Material.EMERALD).values()) {
	    		es += stack.getAmount();
	    	}
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Im Inventar: " + economy().format(es));

	    	bankUsers.put(bPlayer, sign);
	    	moveCheck.put(player.getName(), signBlock.getLocation());

		} else {
			BankPlayer _bPlayer = bankPlayerForSign(sign);
			if (_bPlayer.getPlayer().equals(player)) bPlayer = _bPlayer;
		}
		
		if (bPlayer != null) {
			switchBankState(bPlayer);
		} else {
	    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Sie sehen doch, dass hier gerade jemand bedient wird!");

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
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Diese Person ist leider gerade nicht mehr online! Versuche es später noch einmal!");
						return;
					}
					
					EconomyResponse resp = economy().withdrawPlayer(player.getName(), bPlayer.getTransferTemp());
					if (!resp.transactionSuccess()) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Du hast nicht genügend " + economy().currencyNamePlural() + " auf der Bank für eine Überweisung dieser Größe!");
						return;
					}

					resp = economy().depositPlayer(bPlayer.getTransferPlayer(), bPlayer.getTransferTemp());
					if (!resp.transactionSuccess()) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Die Überweisung schlug fehl. Eventuell besitzt der Spieler noch kein Bankfach? Wende dich eventuell an ein Staff-Mitglied. (Fehler: " + resp.errorMessage +  ")");
						economy().depositPlayer(player.getName(), bPlayer.getTransferTemp());
						return;
					}
					
			    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Überweisung erfolgreich ausgeführt!");
			    	toPlayer.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + player.getDisplayName() + " hat dir gerade " + economy().format(bPlayer.getTransferTemp()) + " auf dein Bankkonto überwiesen!");

					bPlayer.setTransferTemp(0);
					bPlayer.setTransferPlayer(null);

				} else {
					Player toPlayer = getServer().getPlayer(_message);
					if (toPlayer == null || !toPlayer.isOnline()) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Diese Person ist entweder gerade nicht online oder nicht bekannt. Achte auf die korrekte Schreibweise!");
				    	return;
					}
					
					bPlayer.setTransferPlayer(toPlayer.getName());
			    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Überweisung von " + economy().format(bPlayer.getTransferTemp()) + " an " + toPlayer.getName() + ". Zum Bestätigen, schreibe nun einfach 'ok'!");
					
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
						    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Achtung! Es ist nicht genug Platz in deinem Inventar. Wir zahlen daher nur einen Teil aus!");
						    	
							} else {
						    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Achtung! Es ist nicht genug Platz in deinem Inventar. Es kann nichts ausgezahlt werden!");
						    	return;
						    	
							}
							 
						}
						
				    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Ausgezahlt: " + sa.getAmountAsString());

					} else {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Achtung! Du hast nicht genügend " + economy().currencyNamePlural() + " auf der Bank für diese Auszahlung.");
		
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
										
								    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Achtung! Schaffe bitte etwas Platz im Inventar für die Umwandlung von Smaragdblöcken zu Smaragden.");
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
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Achtung! Du hast nicht genügend Emeralds im Inventar für für eine Einzahlung dieser Größe! Es fehlen dir noch " + new SmartAmount(amountCheck).getAmountAsString());

					} else {
						if (blocksFound > 0) inv.removeItem(new ItemStack(Material.EMERALD_BLOCK, blocksFound));
						if (emeraldsFound > 0) inv.removeItem(new ItemStack(Material.EMERALD, emeraldsFound));
						
						player.updateInventory();
						
						economy().depositPlayer(player.getName(), messageValue);
				    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Erfolgreich eingezahlt: " + new SmartAmount(messageValue).getAmountAsString());
					}
					
									
				} break;
				
				case 3: {
					if (!economy().has(player.getName(), messageValue)) {
				    	player.sendMessage(ChatColor.DARK_RED + "[Emerald Bank] " + ChatColor.WHITE + "Du hast nicht genügend " + economy().currencyNamePlural() + " auf der Bank für eine Überweisung dieser Größe!");
				    	return;
					}
					
					bPlayer.setTransferTemp(messageValue);
			    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Überweisungsbetrag: " + economy().format(messageValue) + ". An wen sollen sie gehen?");

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
			player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Hebe nun " + economy().currencyNamePlural() + " ab, indem du den Betrag in den Chat tippst oder klicke erneut auf das Schild.");
		} break;

		case 2: {
				player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "~~~~~~~~~~~~~~~~~~~~~~");
				player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Zahle nun " + economy().currencyNamePlural() + " ein, indem du den Betrag in den Chat tippst oder klicke erneut auf das Schild.");
		} break;

		case 3: {
	    	player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "~~~~~~~~~~~~~~~~~~~~~~");
			player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Überweise nun jemandem " + economy().currencyNamePlural() + ", indem du den Betrag in den Chat tippst oder klicke erneut auf das Schild.");
		} break;

		}
		
		bPlayer.setState(state);
		updateSignState(sign, state);
	}
	
	private void closeBankForPlayer(BankPlayer bPlayer) {
		Player player = bPlayer.getPlayer();
		Sign sign = bPlayer.getSign();
		
		if (player.isOnline()) player.sendMessage(ChatColor.AQUA + "[Emerald Bank] " + ChatColor.GREEN + "Auf wiedersehen!");
		updateSignState(sign, 0);
		
    	bankUsers.remove(bPlayer);
	}

	private void updateSignState(Sign sign, int state) {
		switch (state) {

		case 0: {
			sign.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
			sign.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
			sign.setLine(2, " " + ChatColor.GREEN + "Schalter");
			sign.setLine(3, " " + ChatColor.GREEN + "geöffnet");
		} break;

		case 1: {
			sign.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
			sign.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
			sign.setLine(2, " " + ChatColor.DARK_RED + "Hebe");
			sign.setLine(3, " " + ChatColor.DARK_RED + "etwas ab");
		} break;

		case 2: {
			sign.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
			sign.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
			sign.setLine(2, " " + ChatColor.DARK_RED + "Zahle");
			sign.setLine(3, " " + ChatColor.DARK_RED + "etwas ein");
		} break;

		case 3: {
			sign.setLine(0, " " + ChatColor.AQUA + "Emerald Bank");
			sign.setLine(1, " " + ChatColor.AQUA + ChatColor.BOLD + "~~~~~~~~~~");
			sign.setLine(2, " " + ChatColor.DARK_RED + "überweise");
			sign.setLine(3, " " + ChatColor.DARK_RED + "etwas");
		} break;

		}
		
		sign.update(true);
	}
	
	
	
}

