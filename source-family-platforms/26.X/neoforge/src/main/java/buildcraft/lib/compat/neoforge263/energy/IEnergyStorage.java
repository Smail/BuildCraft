//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.energy;

import java.util.Objects;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import buildcraft.lib.compat.transfer.TransferInterop;

/**
 * BuildCraft-owned Forge Energy contract with int quantities and boolean simulation. NeoForge 26.3 removed its
 * legacy energy storage; native {@link EnergyHandler}s are bridged by {@link TransferInterop}.
 */
public interface IEnergyStorage {
    /** Presents a native energy handler through the legacy FE contract. */
    static IEnergyStorage of(EnergyHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return TransferInterop.importEnergy(handler);
    }

    /** Returns the amount of energy that was (or would have been, if simulated) accepted. */
    int receiveEnergy(int toReceive, boolean simulate);

    /** Returns the amount of energy that was (or would have been, if simulated) extracted. */
    int extractEnergy(int toExtract, boolean simulate);

    int getEnergyStored();

    int getMaxEnergyStored();

    boolean canExtract();

    boolean canReceive();
}
